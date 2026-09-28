# Payment Switch

POS terminallerinden (fiziki veya sanal) gelen kart ödemelerini karşılayan, kartın BIN'ine göre doğru bankaya yönlendiren
ve banka cevabını üye işyerine ulaştıran ödeme switch'i.

Param ve MoneyPay entegrasyonlarında ödemeyi sağlayıcının API'sine gönderen taraftaydım. Bu projede o API'nin arkasındaki
kısmı yazıyorum: idempotency, asenkron dağıtım, banka bazlı izolasyon, timeout ve reversal yönetimi, mutabakat.

```
POS ──► payment-api ──► Kafka ──► routing-service ──► bank.requests.{BANKA} ──► bank-adapter ──► Banka
             ▲                                                                        │
             └──────────────────────── payment.results ◄──────────────────────────────┘
```

Detaylı akış: [docs/02-odeme-akisi.md](docs/02-odeme-akisi.md) · Domain sözlüğü: [docs/01-domain-sozlugu.md](docs/01-domain-sozlugu.md) ·
Terminal entegrasyonu: [docs/03-terminal-kimlik-dogrulama.md](docs/03-terminal-kimlik-dogrulama.md) ·
Routing: [docs/04-routing.md](docs/04-routing.md) ·
Kararlar: [docs/adr](docs/adr) · Ortam kurulumu: [DEVOPS.md](DEVOPS.md)

## 1. Geliştirme Ortamı

- Java 21, Maven 3.9 (wrapper projede mevcut)
- Docker (Postgres, Kafka, Redis ve testlerdeki Testcontainers için)
- IntelliJ IDEA + EnvFile eklentisi. `.env-template` dosyasını `.env` olarak kopyalayıp doldurun.

```bash
docker compose up -d
./mvnw clean verify
./mvnw install -DskipTests
./mvnw -pl payment-api spring-boot:run
./mvnw -pl routing-service spring-boot:run
```

Swagger: http://localhost:8081/swagger-ui.html (payment-api) · http://localhost:8082/swagger-ui.html (routing-service)

## 2. Modüller

| Modül | Port | Sorumluluk |
|---|---|---|
| `common` | - | Ortak response yapısı, exception'lar, `Money`, event'ler |
| `messaging` | - | Outbox, inbox, event okuma, retry ve DLT (Spring Boot auto-configuration) |
| `payment-api` | 8081 | Ödeme karşılama, idempotency, durum yönetimi |
| `routing-service` | 8082 | BIN çözümleme, kural zinciri ile banka seçimi, failover |
| `bank-adapter` | 8083 | Banka protokol dönüşümü, timeout, reversal |
| `bank-simulator` | 8090 | Sahte banka API'leri ve hata senaryoları |

## 3. Yol Haritası

| Faz | Konu | Durum |
|---|---|---|
| PS-0 | Domain sözlüğü, akış diyagramları, ADR-001 | ✅ |
| PS-1 | Multi-module iskelet, `Money`, Payment state machine | ✅ |
| PS-2 | Idempotency-Key ([ADR-002](docs/adr/ADR-002-idempotency.md)) | ✅ |
| PS-2 | Terminal HMAC imzası ([ADR-003](docs/adr/ADR-003-terminal-hmac-imza.md)) | ✅ |
| PS-3 | Transactional outbox, Kafka ([ADR-004](docs/adr/ADR-004-transactional-outbox.md)) | ✅ |
| PS-4 | BIN tabanlı routing, kural zinciri, inbox, DLT ([ADR-005](docs/adr/ADR-005-routing-ve-consumer-tasarimi.md)) | ✅ |
| PS-5 | Bank adapter, resilience, inquiry ve reversal | ⏳ |
| PS-6 | Sonuç işleme, webhook, void ve refund | |
| PS-7 | Double-entry ledger, mutabakat | |
| PS-8 | Ölçekleme, OpenTelemetry, Gatling | |

## 4. Kod Kalitesi

- Para her zaman `Money` ile ve en küçük birim (kuruş) cinsinden `long` olarak taşınır. `double` ve `float` kullanılmaz.
- Kart numarası ve CVV **saklanmaz, loglanmaz**. Sadece BIN ve son 4 hane tutulur.
- Ödeme durumu sadece `Payment.changeStatus` üzerinden değişir.
- Her faz kendi branch'inde geliştirilir, testler yeşil olmadan main'e alınmaz.

## 5. Dokümantasyon Kuralları

### 5.1. Copyright Ayarları

`Settings > Editor > Copyright > Copyright Profiles`

#### Örnek Template

```
Copyright (c) $today.year. Bartuğ Sevindik <bartugsevindik@gmail.com>
```

### 5.2. Intellij Javadoc Templates

#### Method

```
/**
 * <h1>$title$</h1>
 * <p>$description$</p>
 *
 * @param
 * @return
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since ${DATE} - PS-
 */
```

#### Class

```
/**
 * <h1>${NAME}</h1>
 * <p></p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since ${DATE} - PS-
 */
```

## 6. Genel Kod Yapısı

Her servis kendi domain paketine sahiptir (`com.bartugsevindik.paymentswitch.<modül>`). Paket içi katmanlar
`controller`, `service`, `service/impl`, `repository`, `entity`, `dto`, `mapper`, `config` ve `enums` şeklindedir.
Servisler arası paylaşılan her şey `common` modülündedir, güvenilir mesajlaşma altyapısı `messaging` modülündedir.
Her servis aynı Postgres sunucusunda kendi şemasının sahibidir ve servisler arasında veri sadece Kafka event'leriyle geçer.
DB şeması Flyway ile yönetilir (`ddl-auto: validate`).
