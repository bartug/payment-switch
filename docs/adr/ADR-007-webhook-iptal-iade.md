# ADR-007: Webhook, iptal (void) ve iade (refund)

- **Durum:** Kabul edildi
- **Tarih:** 29.09.2026

## Bağlam

- Üye işyeri ödeme sonucunu `GET` ile sürekli sorgulamak (polling) yerine sonuçtan haberdar edilmek istiyor.
- Onaylı ödemeler gün içinde iptal edilebilmeli, sonrasında tamamen ya da kısmen iade edilebilmeli.
- Aynı ödemeye gelen eş zamanlı iadeler toplam tutarı aşmamalı.
- Tekrar gönderilen bir iade isteği ikinci iadeyi oluşturmamalı.

## Kararlar

### 1. Webhook = dışarıya açılan bir outbox

Bildirim kaydı (`webhook_delivery`) ödeme durum değişikliğiyle aynı transaction'da yazılıyor. Dispatcher
`SKIP LOCKED` ile kayıtları alıyor ve HTTP çağrısını transaction dışında yapıyor. Artan aralıklarla 8 deneme var
(10 sn → 6 sa), sonra `FAILED` oluyor ve operasyon tekrar gönderebiliyor. İmza Stripe'ın formatında:
`t=<ts>,v1=<hmac(t.body)>`. Zaman damgası imzaya dahil olduğu için üye işyeri replay'i reddedebiliyor. Detaylar
`docs/06-webhook.md` dokümanında.

**Sıra garantisi verilmiyor.** Tekrar denemeye düşen bir bildirim, sonradan gönderilen bildirimden sonra ulaşabilir.
Sırayı korumak için üye işyeri başına tek bir kuyruk tutmak gerekirdi; bu da bir işyerindeki kesintinin o işyerinin
tüm bildirimlerini bekletmesi demek. Bunun yerine bildirim `paymentStatus` taşıyor, üye işyeri gerektiğinde `GET` ile doğruluyor.

### 2. İptal/iade de satışla aynı banka topic'inden gidiyor

`bank.requests.YKB`, key = `paymentId`. Aynı partition'da olduğu için iptal, satıştan önce adapter'a ulaşamıyor. Ayrı
bir `bank.refunds.YKB` topic'i olsaydı "iade isteği, satış isteğinden önce işlendi" yarışı mümkün olurdu. Mesaj tipi
`event-type` header'ından ayırt ediliyor.

### 3. Satış retry edilmiyor, iptal/iade ediliyor

| | Satış | İptal / İade |
|---|---|---|
| Timeout sonrası | `UNKNOWN` → inquiry → reversal | Aynı `operationId` ile tekrar dene |
| Neden | Bankanın tekrar gelen satışı tanıyacağı garanti değil. Retry çift çekim olabilir. | Banka `operationId`'yi tanıyor. Tekrar gelen iade ikinci iade oluşturmuyor. |

Retry'ın güvenli olup olmadığını belirleyen şey, **karşı sistemin idempotency garantisi**. Gerçek ortamda denendi:
banka cevabı 8 sn geciktirdi, adapter aynı `operationId` ile tekrar denedi, bankada tek iade oluştu.

### 4. İade tutarı: satır kilidi ve bekleyen iadelerin ayrılması

```
TX {
  SELECT ... FROM payment WHERE payment_id = ? FOR UPDATE      ← eş zamanlı iadeler burada sıraya girer
  iade edilebilir = tutar - başarılı iadeler - SONUCU BEKLENEN iadeler
  yeni iade > iade edilebilir → 422
  INSERT payment_operation (PENDING) + idempotency_record + outbox
}
```

- **Satır kilidi olmasaydı:** Aynı anda gelen iki 700 TL'lik iade, 1250,50 TL'lik ödemede ikisi de "kalan 1250,50" görüp kabul edilirdi. Bu durum test edildi.
- **Bekleyen iadeler düşülmeseydi:** Sonucu henüz gelmemiş bir 1000 TL iade varken ikinci bir 300 TL iade kabul edilirdi. Bu durum da test edildi.
- **Optimistic lock yeterli olmazdı:** `@Version` iki iadeden birini hata ile sonlandırırdı; kullanıcı "tekrar deneyin" mesajı alırdı. Pessimistic lock, iadeleri sıraya koyup doğru cevabı veriyor. Satır kilidi sadece aynı ödemenin iadelerini bekletiyor ve bu istekler nadir.
- **DB constraint son savunma:** `refunded_amount <= amount` CHECK'i uygulama hatası olsa bile fazla iadeyi engelliyor.

### 5. İptal kuralları

- Sadece `APPROVED`, hiç iade yapılmamış ve iadesi sürmeyen ödeme iptal edilebiliyor.
- Aynı iş günü ve gün sonu saatinden (varsayılan 23:30, İstanbul) önce yapılmalı. Gün sonundan sonra işlem takasa girmiş oluyor; artık sadece iade yapılabiliyor.
- İptal sürerken (`VOIDING`) iade isteği 409 alıyor. Banka iptali reddederse ödeme `APPROVED`'a dönüyor.

## Sonuçlar

- (+) Tekrar gönderilen iptal/iade istekleri (Idempotency-Key) ve tekrar gelen banka mesajları (inbox + operationId) çift iade oluşturmuyor.
- (+) Üye işyerine her sonuç değişikliği imzalı olarak ulaşıyor. Gerçek ortamda 7 bildirimin 7'sinin imzası doğrulandı.
- (-) İptal/iade denemeleri 10 kez başarısız olursa işlem `MANUAL_REVIEW`'a düşüyor. İadenin bankada yapılıp yapılmadığı bilinmiyor; mutabakatta (PS-7) kontrol edilecek.
- (-) Gün sonu saati sabit bir config. Bankaların gerçek cut-off saatleri farklı; banka bazlı yapılabilir.
- (-) Webhook adresi için SSRF koruması şu an sadece yönlendirmeleri takip etmemekle sınırlı. Özel IP aralıkları reddedilmeli.
