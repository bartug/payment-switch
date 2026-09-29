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
./mvnw -pl bank-adapter spring-boot:run      # 8083
./mvnw -pl bank-simulator spring-boot:run    # 8090
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

> **Lokal varsayılanlar gerçek anahtar değildir.** Şifreleme anahtarları bilerek okunabilir değerlerden üretildi
> (örn. `base64("LOCAL-DEV-ONLY-terminal-key-0001")`); repoyu tarayan secret scanner'lar ve okuyanlar bunların
> sahte olduğunu görebilsin diye. `production` profilinde bu değerlerin hiçbiri yoktur; env verilmezse uygulama açılmaz.
> Gerçek anahtar üretmek için: `openssl rand -base64 32`

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
| `CARD_VAULT_ENCRYPTION_KEY` | lokal için sabit bir key | Kart verisini şifreleyen AES-256 key. Terminal key'inden **farklı** olmalı. **Production'da zorunlu.** |
| `CARD_VAULT_TTL` | `15m` | Bankaya ulaşamayan ödemelerin kart verisinin en fazla saklanma süresi |
| `INTERNAL_API_TOKEN` | lokal için sabit bir token | payment-api `/internal/**` ile bank-adapter arasında ortak. **Production'da zorunlu**, mTLS ile değiştirilmeli. |
| `BANK_CODES` | `QNB,YKB,GARANTI,ISBANK,AKBANK` | bank-adapter'ın işlem gönderdiği bankalar. Production'da banka başına deployment: `BANK_CODES=YKB` |
| `BANK_API_URL` / `PAYMENT_API_URL` | `http://localhost:8090` / `http://localhost:8081` | |
| `BANK_READ_TIMEOUT` | `5s` | Bu süre dolarsa işlem `UNKNOWN` olur |
| `BUSINESS_DAY_CUTOFF` | `23:30` | Bu saatten sonra o günün ödemeleri iptal edilemez, sadece iade edilebilir (İstanbul saati) |
| `WEBHOOK_POLL_INTERVAL` | `1s` | Webhook dispatcher'ın bekleyen bildirimlere bakma aralığı |
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

### Banka entegrasyonu

| Metrik | Anlamı | Alarm önerisi |
|---|---|---|
| `resilience4j_circuitbreaker_state{name="YKB"}` | Circuit durumu | OPEN olduğunda |
| `bank_transactions_total{bank,status}` | Banka ve durum bazında işlem sayısı | UNKNOWN oranı artıyorsa banka yavaşlıyor |
| `bank_transactions_manual_review_total` | Reversal da başarısız olan işlemler | **> 0 ise hemen incele**, mutabakatta kontrol et |
| `resilience4j_bulkhead_available_concurrent_calls` | Bankaya giden eş zamanlı çağrı kapasitesi | 0'a yaklaşıyorsa |

```bash
curl -s localhost:8083/v1/admin/circuits
curl -s localhost:8083/v1/admin/transactions/<paymentId>

# Chaos: cevap geciksin / banka kapansın / normale dönsün
curl -s -X PUT localhost:8090/v1/admin/banks/YKB/chaos -H 'Content-Type: application/json' \
  -d '{"down":false,"latencyMs":0,"failureRate":0,"lateResponseRate":1,"lateResponseMs":8000}'
curl -s -X PUT localhost:8090/v1/admin/banks/YKB/chaos -H 'Content-Type: application/json' \
  -d '{"down":true,"latencyMs":0,"failureRate":0,"lateResponseRate":0,"lateResponseMs":0}'
curl -s -X PUT localhost:8090/v1/admin/banks/YKB/chaos -H 'Content-Type: application/json' \
  -d '{"down":false,"latencyMs":0,"failureRate":0,"lateResponseRate":0,"lateResponseMs":0}'
```

Senaryolar ve beklenen sonuçlar: [docs/05-banka-entegrasyonu.md](docs/05-banka-entegrasyonu.md)

### İptal ve iade

```bash
scripts/pos-request.sh POST /v1/payments/<paymentId>/refunds '{"amount":400.00}'
scripts/pos-request.sh POST /v1/payments/<paymentId>/void ''
scripts/pos-request.sh GET  /v1/payments/<paymentId>/operations
```

| Metrik | Alarm önerisi |
|---|---|
| `bank_operations_total{type,status}` | FAILED oranı artıyorsa |
| `bank_operations_manual_review_total` | **> 0 ise incele**: iadenin bankada yapılıp yapılmadığı bilinmiyor |

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

Prometheus, Grafana ve Jaeger PS-8 ile eklenecek.

## 🚀 Deploy

- İmajlar multi-stage build ile alınır. Katmanlar (dependencies / application) ayrı olduğu için kod değişikliğinde sadece son katman değişir.
- Container non-root kullanıcı (`psadmin`) ile çalışır.
- Kubernetes'te readiness probe olarak `/actuator/health/readiness` kullanılmalı.
- `bank-adapter` her banka için ayrı deployment olarak çıkılır (`BANK_CODES=YKB`) ve ölçeklemesi banka bazında yapılır.
  Yoğun bankanın pod sayısı artırılırken topic partition sayısı da en az pod × `BANK_CONSUMER_CONCURRENCY` olmalıdır.
- `/internal/**` uç noktaları ingress'e açılmamalıdır; sadece cluster içinden erişilmelidir.

## 🧹 Temizlik

```bash
docker compose down -v
```
