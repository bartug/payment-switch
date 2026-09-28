# Terminal Kimlik Doğrulama

`/v1/payments` altındaki her istek, terminalin kendi secret'ı ile **HMAC-SHA256** imzalanır.
Bu doküman bir POS yazılımının (terminal SDK) entegrasyon için bilmesi gerekenleri anlatır.

## 1. Terminal tanımlama

```bash
curl -s -X POST http://localhost:8081/v1/admin/terminals \
  -H 'Content-Type: application/json' \
  -d '{"terminalId":"TRM00000001","merchantId":"MRC0000001","terminalType":"VIRTUAL"}'
```

Cevaptaki `secret` **sadece bu cevapta** döner. Sunucuda şifreli saklanır ve tekrar görüntülenemez.
Kaybedilirse terminal pasife alınıp yeniden tanımlanır.

## 2. Header'lar

| Header | Örnek | Açıklama |
|---|---|---|
| `X-Terminal-Id` | `TRM00000001` | Terminal numarası |
| `X-Timestamp` | `1790582400` | Unix epoch **saniye**. Sunucu saatinden en fazla ±5 dk farklı olabilir. |
| `X-Signature` | `k1Jd...=` | `Base64(HMAC-SHA256(secret, stringToSign))` |
| `Idempotency-Key` | `7c9e6679-...` | POST'ta zorunlu, imzaya dahil |

Her header **bir kez** gönderilmelidir. Tekrarlanan header'lı istekler 400 ile reddedilir.

## 3. İmzalanan metin

Satırlar `\n` ile birleştirilir, sonda satır sonu yoktur:

```
POST
/v1/payments
1790582400
7c9e6679-7425-40de-944b-e07fc1f90ae7
3f2a...(body'nin SHA-256 hex değeri)
```

| Satır | Değer |
|---|---|
| 1 | HTTP method, büyük harf |
| 2 | Path. Query string varsa `?` ile birlikte. |
| 3 | `X-Timestamp` değeri |
| 4 | `Idempotency-Key`. Yoksa (GET) boş satır. |
| 5 | Body'nin SHA-256 hex değeri. Body yoksa boş stringin hash'i. |

Body **byte byte** imzalandığı gibi gönderilmelidir. İmzadan sonra JSON'u yeniden formatlamak (boşluk, alan sırası) imzayı bozar.

## 4. Örnek

`scripts/pos-request.sh` bu algoritmanın bash/openssl ile yazılmış halidir:

```bash
export TERMINAL_ID=TRM00000001
export TERMINAL_SECRET=<terminal oluştururken dönen secret>

scripts/pos-request.sh POST /v1/payments \
  '{"amount":1250.50,"currency":"TRY","installmentCount":3,"cardNumber":"5400617020092306","expiryMonth":"12","expiryYear":"28","cvv":"000"}'

scripts/pos-request.sh GET /v1/payments/<paymentId>
```

Retry denemek için aynı key ile tekrar gönderin: `IDEMPOTENCY_KEY=<key> scripts/pos-request.sh POST ...`

## 5. Hata cevapları

| Durum | HTTP | Mesaj |
|---|---|---|
| İmza header'ları eksik | 401 | `X-Terminal-Id, X-Timestamp ve X-Signature header'ları zorunludur.` |
| Zaman damgası pencere dışında | 401 | `İstek zaman damgası geçersiz ya da süresi dolmuş.` |
| İmza hatalı **veya** terminal yok | 401 | `İstek imzası doğrulanamadı.` |
| Terminal pasif | 403 | `Terminal işlem almaya kapalı.` |
| Header tekrarlanmış | 400 | `<header> header'ı birden fazla gönderilemez.` |
