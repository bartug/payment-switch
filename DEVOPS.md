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
./mvnw -pl payment-api -am install -DskipTests
./mvnw -pl payment-api spring-boot:run
```

Flyway migration'ları uygulama açılırken otomatik çalışır.

### Step 3: Uygulamayı container olarak çalıştırma

```bash
docker compose --profile app up -d --build
```

Tek bir servisin imajını almak için build context **kök dizin** olmalı. `common` modülü her servise dahil ediliyor:

```bash
docker build -f payment-api/Dockerfile -t payment-switch/payment-api:1.0.0 .
```

### Step 4: Test

```bash
curl -s -X POST http://localhost:8081/v1/payments \
  -H 'Content-Type: application/json' \
  -d '{"merchantId":"MRC0000001","terminalId":"TRM00000001","terminalType":"VIRTUAL","amount":1250.50,"currency":"TRY","installmentCount":3,"cardNumber":"5400617020092306","expiryMonth":"12","expiryYear":"28","cvv":"000"}'
```

## ⚙️ Ortam Değişkenleri

`.env-template` dosyasındaki değişkenler kullanılır. Hepsinin lokal için varsayılan değeri var.

| Değişken | Varsayılan | Açıklama |
|---|---|---|
| `PG_HOST` / `PG_PORT` / `PG_DB` | `localhost` / `5432` / `payment_switch` | |
| `PG_USERNAME` / `PG_PASSWORD` | `payment` / `payment` | Production'da secret store'dan gelmeli |
| `PG_POOL_SIZE` | `20` | Hikari havuz boyutu |
| `KAFKA_BROKERS` | `localhost:9092` | |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | |
| `SPRING_PROFILES_ACTIVE` | - | `production` açıldığında Swagger kapanır, loglar ECS formatına geçer |

## 📊 İzleme

- Health: `GET /actuator/health/liveness`, `GET /actuator/health/readiness`
- Metrikler: `GET /actuator/prometheus`
- Kafka consumer lag: Kafka UI > Consumers
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
