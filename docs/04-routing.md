# Routing: Ödeme Hangi Bankaya Gider?

routing-service, `payment.requested` topic'inden gelen her ödeme için bir banka seçer ve isteği o bankanın topic'ine
(`bank.requests.{BANKA}`) gönderir. Kararın sonucunu `payment.routing.results` ile payment-api'ye bildirir.

```
payment.requested ──► routing-service
                        1. BIN çözümle (8 hane → 6 hane, Caffeine cache)
                        2. Aktif bankaları al (5 sn cache)
                        3. Kural zincirini çalıştır
                        4. TX { inbox + routing_decision + outbox }
                            ├─► bank.requests.YKB      (sadece yönlendirildiyse)
                            └─► payment.routing.results (her durumda)
```

## 1. Kural zinciri

Kurallar sırayla çalışır. Karar veren ilk kural zinciri bitirir.

| Sıra | Kural | Koşul | Sonuç |
|---|---|---|---|
| 10 | `UnknownBinInstallmentRule` | Taksitli ve BIN tanımsız | ❌ `UNKNOWN_BIN_INSTALLMENT` |
| 20 | `NonCreditInstallmentRule` | Taksitli ve kart kredi kartı değil | ❌ `NON_CREDIT_INSTALLMENT` |
| 30 | `InstallmentProgramRule` | Taksitli | Program bankası aktifse ✅ `ON_US_INSTALLMENT`, değilse ❌ `PROGRAM_BANK_UNAVAILABLE` |
| 40 | `LowestCostRule` | Tek çekim | ✅ En düşük komisyonlu aktif banka (`LOWEST_COST`), hiç aktif banka yoksa ❌ `NO_ACTIVE_BANK` |

**Taksitte neden failover yok?** Taksit programı (World, Bonus...) kartın bankasına aittir. Taksitli bir işlemi başka
bankanın POS'undan geçirirsek o banka taksiti tanımaz. Ya işlem reddedilir ya da tek çekim olarak geçer ve kart sahibi
tüm tutarı bir anda öder. Bu yüzden program bankası kapalıysa işlem reddediliyor.

**Tek çekimde maliyet nasıl hesaplanıyor?** Her aktif banka için kart o bankanınsa on-us oranı, değilse off-us oranı
alınıyor ve en düşüğü seçiliyor. On-us oranı her zaman daha düşük olduğu için kart genelde kendi bankasına gidiyor.
Kendi bankası kapalıysa bir sonraki en ucuz bankaya kayıyor; failover budur.

## 2. Örnek veri ile test kartları

BIN tablosu **örnek veridir**, gerçek BIN listesi değildir (`V2__seed_sample_bins_and_banks.sql`).

| Kart numarası | BIN | Banka / Program | Tip |
|---|---|---|---|
| `5400617020092306` | 540061 | YKB / World | Kredi |
| `4111111111111111` | 411111 | Garanti / Bonus | Kredi |
| `4242424242424242` | 424242 | İş Bankası / Maximum | Kredi |
| `5555555555554444` | 555555 | QNB / CardFinans | Kredi |
| `5200828282828210` | 520082 | Akbank / Axess | Kredi |
| `4000056655665556` | **40000566** | YKB / - | Banka kartı (8 haneli BIN, 400005 Garanti'yi ezer) |
| `9792001234567890` | 979200 | İş Bankası / - | Troy banka kartı |
| `6011111111111117` | - | Tanımsız | - |

| Banka | On-us | Off-us |
|---|---|---|
| QNB | %1,70 | %1,99 |
| YKB | %1,80 | %2,20 |
| Garanti | %1,75 | %2,15 |
| İş Bankası | %1,85 | %2,25 |
| Akbank | %1,90 | %2,30 |

### Gerçek ortamda denenen senaryolar

| Senaryo | Durum | Banka | Neden |
|---|---|---|---|
| World kart, 3 taksit | ROUTED | YKB | Program bankası |
| World kart, tek çekim | ROUTED | YKB | On-us %1,80 < en ucuz off-us %1,99 |
| Bonus kart, 6 taksit | ROUTED | Garanti | Program bankası |
| CardFinans, tek çekim | ROUTED | QNB | On-us |
| Troy banka kartı, 6 taksit | FAILED | - | Banka kartıyla taksit yapılamaz |
| Troy banka kartı, tek çekim | ROUTED | İş Bankası | On-us %1,85 < QNB off-us %1,99 |
| Tanımsız BIN, 3 taksit | FAILED | - | Program bilinmiyor |
| Tanımsız BIN, tek çekim | ROUTED | QNB | Hepsi off-us, en ucuz QNB |
| **YKB pasif**, World tek çekim | ROUTED | **QNB** | Failover |
| **YKB pasif**, World 3 taksit | FAILED | - | Program bankası kapalı, failover yok |

## 3. Denemek için

```bash
# Ödeme oluşturmadan karar
curl -s -X POST localhost:8082/v1/admin/routing/simulate \
  -H 'Content-Type: application/json' -d '{"cardBin":"54006170","installmentCount":1}'

# Bankayı kapatıp tekrar dene → QNB'ye kayar
curl -s -X PUT localhost:8082/v1/admin/banks/YKB/passive
curl -s -X PUT localhost:8082/v1/admin/banks/YKB/active
```

Swagger: http://localhost:8082/swagger-ui.html

## 4. Yeni kural eklemek

`RoutingRule` arayüzünü uygulayan bir `@Component` yazıp `@Order` ile sıraya koymak yeterli. Kurallar sadece
`RoutingContext`'e bakar, DB'ye ya da Kafka'ya gitmez; `RoutingEngineTest` gibi Spring'siz test edilebilir.

Örnek fikirler: ticari kartları belirli bir bankaya yönlendirmek, bankanın saatlik hacim limitini aşınca diğerine kaydırmak,
bankanın son 5 dakikadaki onay oranı düşükse ağırlığını azaltmak (PS-5'ten gelecek sağlık verisi ile).
