# DevOps

Payment Switch servislerinin lokal ve container ortamında ayağa kaldırılması.

## 📋 Ön Gereksinimler

| Araç | Sürüm |
|---|---|
| Java | 21 |
| Docker / Docker Compose | 24+ / v2 |
| Maven | Wrapper kullanılıyor (`./mvnw`) |

## 🐳 Docker ile Ayağa Kaldırma

### Step 1: Altyapı

```bash
docker compose up -d
docker compose ps
```

| Servis | Adres |
|---|---|
| PostgreSQL | `localhost:5432` |
| Redis | `localhost:6379` |
| Kafka | `localhost:9092` (container içinden `kafka:29092`) |
| Kafka UI | http://localhost:8095 |

### Step 2: Uygulamayı lokalde çalıştırma

```bash
./mvnw install -DskipTests
./mvnw -pl payment-api spring-boot:run       # 8081
./mvnw -pl routing-service spring-boot:run   # 8082
```

Flyway migration'ları uygulama açılırken otomatik çalışır.

### Step 3: Uygulamayı container olarak çalıştırma

```bash
docker compose --profile app up -d --build
```

Tek bir servisin imajını almak için build context **kök dizin** olmalı. `common` ve `messaging` modülleri servise dahil ediliyor:

```bash
docker build -f payment-api/Dockerfile -t payment-switch/payment-api:1.0.0 .
```

### Step 4: Test

Önce bir terminal tanımlanır, dönen `secret` ile istekler imzalanır:

```bash
curl -s -X POST http://localhost:8081/v1/admin/terminals \
  -H 'Content-Type: application/json' \
  -d '{"terminalId":"TRM00000001","merchantId":"MRC0000001","terminalType":"VIRTUAL"}'

export TERMINAL_ID=TRM00000001
export TERMINAL_SECRET=<dönen secret>

scripts/pos-request.sh POST /v1/payments \
  '{"amount":1250.50,"currency":"TRY","installmentCount":3,"cardNumber":"5400617020092306","expiryMonth":"12","expiryYear":"28","cvv":"000"}'
```

İmza algoritması: [docs/03-terminal-kimlik-dogrulama.md](docs/03-terminal-kimlik-dogrulama.md)

Birkaç saniye sonra `scripts/pos-request.sh GET /v1/payments/<paymentId>` ödemenin `ROUTED` durumuna geçtiğini ve
hangi bankaya gittiğini gösterir. Test kartları ve routing senaryoları: [docs/04-routing.md](docs/04-routing.md)

## ⚙️ Ortam Değişkenleri

`.env-template` dosyasındaki değişkenler kullanılır. Hepsinin lokal için varsayılan değeri var.

| Değişken | Varsayılan | Açıklama |
|---|---|---|
| `PG_HOST` / `PG_PORT` / `PG_DB` | `localhost` / `5432` / `payment_switch` | |
| `PG_USERNAME` / `PG_PASSWORD` | `payment` / `payment` | Production'da secret store'dan gelmeli |
| `PG_POOL_SIZE` | `20` | Hikari havuz boyutu |
| `KAFKA_BROKERS` | `localhost:9092` | |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | Idempotency kilidi. Kapalıysa uygulama çalışmaya devam eder (fail-open). |
| `IDEMPOTENCY_HASH_SECRET` | `local-dev-secret-change-me` | İstek hash'i için HMAC secret. **Production'da zorunlu**, varsayılan yok. |
| `IDEMPOTENCY_RECORD_TTL` / `IDEMPOTENCY_LOCK_TTL` | `24h` / `10s` | |
| `TERMINAL_SECRET_MASTER_KEY` | lokal için sabit bir key | Terminal secret'larını şifreleyen AES-256 key (Base64, 32 byte). `openssl rand -base64 32`. **Production'da zorunlu**, KMS/Vault'tan gelmeli. |
| `TERMINAL_TIMESTAMP_TOLERANCE` | `5m` | Terminal saati ile sunucu saati arasındaki izin verilen fark |
| `OUTBOX_POLL_INTERVAL` | `200ms` | Outbox relay'in Kafka'ya gönderim aralığı |
| `KAFKA_TOPIC_PARTITIONS` / `KAFKA_TOPIC_REPLICAS` | `6` / `1` | Uygulamanın açılışta oluşturduğu topic'ler için. Production'da replicas en az 3 olmalı. |
| `ROUTING_CONSUMER_CONCURRENCY` | `3` | routing-service consumer thread sayısı. Partition sayısından fazlası boşta kalır. |
| `SPRING_PROFILES_ACTIVE` | - | `production` açıldığında Swagger kapanır, loglar ECS formatına geçer |

## 📊 İzleme

- Health: `GET /actuator/health/liveness`, `GET /actuator/health/readiness`
- Metrikler: `GET /actuator/prometheus`
- Kafka consumer lag: Kafka UI > Consumers

### Outbox

| Metrik | Anlamı | Alarm önerisi |
|---|---|---|
| `outbox_pending_events` | Kafka'ya gönderilmeyi bekleyen event sayısı | Sürekli artıyorsa |
| `outbox_oldest_pending_seconds` | En eski bekleyen event'in yaşı | > 30 sn: ödemeler bankaya gitmiyor |

```bash
# Bekleyen event'ler ve son hata
docker exec ps-postgres psql -U payment -d payment_switch \
  -c "select aggregate_id, attempts, last_error, created_date from outbox_event where published_at is null order by id limit 20"

# Topic'teki mesajları header'larıyla izleme
docker exec ps-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic payment.requested --from-beginning --property print.key=true --property print.headers=true
```

Kafka kapalıyken ödeme alınmaya devam eder. Event'ler outbox'ta birikir ve Kafka geri geldiğinde sırayla gönderilir.

### Dead letter topic'ler (DLT)

İşlenemeyen mesajlar 3 denemeden sonra `<topic>.DLT`'ye gider (bozuk mesajlar hemen) ve `Message sent to DLT` ile ERROR log'lanır.
DLT'de mesaj olması her zaman incelenmelidir; o ödeme akışın dışında kalmıştır.

```bash
docker exec ps-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic payment.requested.DLT --from-beginning --property print.key=true --property print.headers=true
```

### Routing

| Metrik | Anlamı |
|---|---|
| `routing_decisions_total{outcome,reason,bank}` | Banka ve sebep bazında karar sayısı. Failover oranı ve red sebepleri buradan izlenir. |
| `cache_gets_total{cache="bin_lookup",result}` | BIN cache hit/miss |

Banka durumunu elle değiştirme (operasyon):

```bash
curl -s localhost:8082/v1/admin/banks
curl -s -X PUT localhost:8082/v1/admin/banks/YKB/passive
curl -s -X PUT localhost:8082/v1/admin/banks/YKB/active
```
- Prometheus, Grafana ve Jaeger PS-8 ile eklenecek.

## 🚀 Deploy

- İmajlar multi-stage build ile alınır. Katmanlar (dependencies / application) ayrı olduğu için kod değişikliğinde sadece son katman değişir.
- Container non-root kullanıcı (`psadmin`) ile çalışır.
- Kubernetes'te readiness probe olarak `/actuator/health/readiness` kullanılmalı.
- `bank-adapter` her banka için ayrı deployment olarak çıkılır (`BANK_CODE=YKB` vb.) ve ölçeklemesi banka bazında yapılır. Detaylar PS-5 ile gelecek.

## 🧹 Temizlik

```bash
docker compose down -v
```
