# Webhook: Üye İşyerine Ödeme Bildirimi

Ödeme sonuçlandığında (onay, red, başarısız, iptal) üye işyerinin tanımladığı adrese imzalı bir `POST` gider.
Üye işyerinin `GET /v1/payments/{id}` ile sürekli sorgu atmasına (polling) gerek kalmaz.

## 1. Nasıl çalışıyor?

```
payment-api
  TX { payment.status = APPROVED + webhook_delivery (PENDING) }     ← aynı transaction (outbox mantığı)
                                     │
  WebhookDispatcher (1 sn) ──────────┘  FOR UPDATE SKIP LOCKED → POST (TX dışında)
        ├─ 2xx          → DELIVERED
        └─ diğer / hata → 10 sn, 30 sn, 2 dk, 10 dk, 30 dk, 1 sa, 3 sa, 6 sa sonra tekrar → FAILED
```

- **Bildirim kaybolmaz.** Durum değişikliği ile bildirim kaydı aynı transaction'da. Durum değiştiyse bildirim kesin yazılmıştır.
- **Tekrar gelebilir (at-least-once).** Üye işyeri 200 döndü ama cevap bize ulaşmadıysa aynı bildirim tekrar gider. Her denemede aynı `id` kullanılıyor.
- **Sıra garantisi yok.** Önce gönderilen bildirim tekrar denemeye düşerse sonrakinden sonra ulaşabilir. Üye işyeri bildirimdeki `data.paymentStatus`'a güvenmeli, şüpheye düştüğünde `GET` ile ödemenin güncel halini sorgulamalı.
- **Ara durumlar bildirilmez.** `PENDING`, `ROUTED` ve `UNKNOWN` için bildirim gitmez.

| Olay | Ne zaman |
|---|---|
| `payment.approved` | Banka onayladı (inquiry ile sonradan netleşen dahil) |
| `payment.declined` | Banka reddetti (yetersiz bakiye vb.) |
| `payment.failed` | İşlem bankaya gönderilemedi (routing reddetti, banka kapalı) |
| `payment.reversed` | Cevapsız kalan işlem teknik iptal ile geri alındı, para çekilmedi |

## 2. İstek

```http
POST /webhooks/payments HTTP/1.1
Content-Type: application/json
X-Webhook-Id: 3f5c1b0e-6c1d-4b8e-9f0b-2f4b7a1e9d21
X-Webhook-Event: payment.approved
X-Webhook-Signature: t=1790668800,v1=5f2b...e91c

{
  "id": "3f5c1b0e-6c1d-4b8e-9f0b-2f4b7a1e9d21",
  "type": "payment.approved",
  "createdAt": "2026-09-29T10:00:00Z",
  "data": {
    "paymentId": "8f14e45f-...",
    "paymentStatus": "APPROVED",
    "amount": 1250.50,
    "currency": "TRY",
    "installmentCount": 3,
    "maskedCardNumber": "540061******2306",
    "bankCode": "YKB",
    "authCode": "482915",
    "responseCode": "00"
  }
}
```

## 3. Üye işyeri tarafında doğrulama

1. `X-Webhook-Signature` header'ını `t` ve `v1` olarak ayır.
2. `t` şu anki zamandan 5 dakikadan eskiyse **reddet** (replay koruması).
3. `HMAC-SHA256(webhookSecret, t + "." + <ham body>)` hesapla ve `v1` ile **sabit sürede** karşılaştır.
4. `id` daha önce işlendiyse 200 dön ama tekrar işleme.
5. **Hemen 2xx dön**, asıl işi arka planda yap. 5 sn içinde cevap gelmezse deneme başarısız sayılır.

```java
String[] parts = signatureHeader.split(",");
long t = Long.parseLong(parts[0].substring(2));
String v1 = parts[1].substring(3);

if (Math.abs(Instant.now().getEpochSecond() - t) > 300) {
    return ResponseEntity.status(400).build();
}
Mac mac = Mac.getInstance("HmacSHA256");
mac.init(new SecretKeySpec(webhookSecret.getBytes(UTF_8), "HmacSHA256"));
String expected = HexFormat.of().formatHex(mac.doFinal((t + "." + rawBody).getBytes(UTF_8)));
if (!MessageDigest.isEqual(expected.getBytes(UTF_8), v1.getBytes(UTF_8))) {
    return ResponseEntity.status(401).build();
}
```

İmza, JSON'a çevrilmiş nesne üzerinden değil, **ham body** üzerinden hesaplanmalı. Framework body'yi parse edip
yeniden serialize ederse alan sırası ya da boşluklar değişir ve imza tutmaz.

## 4. Operasyon

```bash
# Üye işyerini webhook adresiyle tanımla (secret bir kez döner)
curl -s -X POST localhost:8081/v1/admin/merchants -H 'Content-Type: application/json' \
  -d '{"merchantId":"MRC0000001","name":"Taksici Ahmet","webhookUrl":"https://merchant.example.com/hooks"}'

# Secret sızdıysa yenile
curl -s -X POST localhost:8081/v1/admin/merchants/MRC0000001/webhook-secret

# "Bildirim gelmedi" şikayeti: teslimat geçmişi ve tekrar gönderim
curl -s "localhost:8081/v1/admin/webhooks?paymentId=<paymentId>"
curl -s -X POST localhost:8081/v1/admin/webhooks/<deliveryId>/redeliver
```

| Metrik | Alarm önerisi |
|---|---|
| `webhook_deliveries_total{result="failed"}` | Artıyorsa üye işyerinin sunucusu uzun süredir cevap vermiyor |
| `webhook_deliveries_total{result="retry"}` | Ani artış: üye işyeri tarafında kesinti |

## 5. Güvenlik notları

- **Yönlendirme takip edilmez.** Üye işyerinin adresi iç ağdaki bir adrese yönlendirirse (SSRF) bildirim oraya gitmez.
- Production'da webhook adresi sadece `https` olmalı ve özel IP aralıklarına (10.x, 172.16.x, 192.168.x, 169.254.x) çözümlenen adresler reddedilmeli.
- Secret DB'de şifreli. Hash'lenemez, çünkü imzalamak için secret'ın kendisi gerekiyor. `whsec_` ön eki, secret sızarsa hangi sisteme ait olduğunu belli ediyor.
