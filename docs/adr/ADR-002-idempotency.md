# ADR-002: Idempotency-Key tasarımı

- **Durum:** Kabul edildi
- **Tarih:** 28.09.2026

## Bağlam

POS, ödeme isteğini gönderdikten sonra cevabı alamazsa (ağ kopması, timeout) isteği tekrar gönderir. İlk istek bize
ulaşıp işlenmiş olabilir. İkinci istek yeni bir ödeme oluşturursa kart sahibi **iki kez ücretlendirilir**.

## Karar

### 1. Client her ödeme denemesi için bir `Idempotency-Key` üretir

- Header zorunludur. Format: 8-64 karakter, `[A-Za-z0-9_-]`. UUID önerilir.
- Key HTTP isteği başına değil, **ödeme denemesi başına** üretilir. Retry'da aynı key gönderilir.
- Key üye işyeri bazında tekildir: `(merchant_id, idempotency_key)`.

### 2. Katmanlar

```
İstek → [1] DB'de kayıt var mı? ── var → hash aynı mı? ── evet → replay (202, Idempotent-Replayed: true)
                                                       └─ hayır → 422
      → [2] Redis SET NX PX 10s ── alınamadı → DB'ye tekrar bak → yoksa 409 + Retry-After: 1
      → [3] TX { payment INSERT + idempotency_record INSERT }
                └─ unique constraint hatası → rollback → replay
      → kilidi bırak
```

| Katman | Rolü | Çökerse |
|---|---|---|
| Redis kilidi | Eş zamanlı kopyaları DB'ye gitmeden eler, DB connection'larını korur | Fail-open. Kilit atlanır, DB constraint devreye girer. |
| DB unique constraint | **Asıl garanti.** Aynı key ile ikinci kayıt fiziksel olarak yazılamaz. | DB yoksa zaten ödeme alınamaz. |

### 3. Idempotency kaydı ödeme ile aynı transaction'da yazılır

Ayrı bir `IN_PROGRESS → COMPLETED` akışı kullanılmadı. O yaklaşımda "kayıt IN_PROGRESS yazıldı, ödeme oluştu, uygulama
COMPLETED yazamadan öldü" gibi bir ara durum oluşur ve bu kayıt sonsuza kadar takılı kalır. Tek transaction ile ya ikisi
birden yazılır ya hiçbiri yazılmaz.

Transaction bilinçli olarak metodun tamamını kapsamaz (`TransactionTemplate`). Redis kilidi beklenirken
DB connection tutulmaz.

### 4. Tekrar eden istekte ödemenin güncel hali döner

Stripe ilk cevabın birebir kopyasını döner. Bizde ilk cevap her zaman `PENDING` olduğu için kopyasını dönmenin POS'a
faydası yok. Bunun yerine ödemenin **güncel** durumu (`APPROVED`, `DECLINED`...) dönüyor. HTTP status aynı kalıyor (`202`)
ve `Idempotent-Replayed: true` header'ı ekleniyor.

### 5. Request hash: HMAC-SHA256 + kanonik JSON

- İstekte kart numarası var. Düz SHA-256 ile hash'lenirse, kart numarası uzayı küçük olduğu için brute-force ile geri
  çözülebilir (PCI DSS). Bu yüzden server secret ile **HMAC** kullanılıyor.
- Alanlar alfabetik sıralanıyor ve `1250.50` ile `1250.5` aynı kabul ediliyor. Böylece aynı istek farklı yazıldığında 422 dönmüyor.
- `WRITE_ONLY` alanlar (kart numarası, CVV) da hash'e dahil ediliyor. Aksi halde sadece kartı farklı olan iki istek aynı sayılırdı.

### 6. TTL

| | Süre | Neden |
|---|---|---|
| Redis kilidi | 10 sn | Bir isteğin işlenme süresinden biraz uzun. Uygulama ölürse kilit kendiliğinden düşer. |
| DB kaydı | 24 saat | POS'un en uzun retry penceresinden uzun. Gün sonuna kadar void yapılabildiği için. |

Süresi dolan kayıtlar 10 dakikada bir, 1000'erli parçalar halinde silinir.

## Sonuçlar

- (+) Redis çökse bile çift ödeme oluşmuyor (`IdempotencyWithoutRedisTest`).
- (+) Eş zamanlı 20 kopyadan sadece bir ödeme oluşuyor (`IdempotencyIntegrationTest`).
- (-) Redis kapalıyken her istek timeout kadar (~300 ms) gecikiyor. PS-5'te Redis önüne circuit breaker eklenecek.
- (-) 24 saatten sonra aynı key gelirse yeni ödeme oluşur. Buna karşı iş kuralı bazlı duplicate kontrolü (aynı terminal,
  kart ve tutar ile kısa sürede gelen işlem) ayrıca düşünülecek.
- (-) Cleanup job birden fazla pod'da aynı anda çalışabilir. Bu zararsız (silme işlemi idempotent), ama gereksiz yük
  oluşturuyor. İleride ShedLock eklenebilir.
