# ADR-006: Banka entegrasyonu, cevapsız işlemler ve card vault

- **Durum:** Kabul edildi
- **Tarih:** 28.09.2026

## Bağlam

Ödeme sistemlerindeki en pahalı hata, **bankanın parayı çektiği ama bizim bunu bilmediğimiz** durum. Kart sahibi
ödeme başarısız sanıp tekrar ödüyor, çift çekim oluşuyor. Bankayla konuşan her kod şu sorulara cevap vermek zorunda:

1. İstek gönderildi ama cevap gelmedi. Para çekildi mi?
2. Banka çöktü. Diğer bankalara giden işlemler etkilenecek mi?
3. Bankaya kart numarası gitmeli, ama Kafka'ya kart numarası yazılamaz. Kart verisi bankaya nasıl ulaşacak?

## Kararlar

### 1. Satış isteği asla retry edilmez; UNKNOWN → inquiry → reversal

Hata tipleri "isteğin bankaya ulaşıp ulaşmadığına" göre sınıflandırılıyor (bkz. `docs/05-banka-entegrasyonu.md`).
Ulaşıp ulaşmadığı bilinmiyorsa işlem `UNKNOWN` oluyor ve recovery job'u önce inquiry, gerekirse reversal yapıyor.
Inquiry işlemi bulamazsa da reversal yapılıyor, çünkü istek hâlâ bankanın kuyruğunda olabilir. Reversal de başarısız
olursa işlem `MANUAL_REVIEW`'a düşüyor ve alarm üretiliyor. Bu işlemler gün sonu mutabakatında ayrıca kontrol edilmeli.

**Alternatif: Idempotency key ile retry.** Banka orderId'yi idempotent işliyorsa retry güvenli olurdu. Ama her bankanın
sanal POS'u bunu garanti etmiyor ve bir bankanın bu davranışı sessizce değişebilir. Inquiry her bankada var.

### 2. Banka çağrısı transaction dışında

```
TX1 { inbox + SENDING }  →  HTTP (5 sn'ye kadar)  →  TX2 { sonuç + outbox }
```

HTTP çağrısı transaction içinde olsaydı banka 5 sn cevap vermediğinde DB connection 5 sn tutulurdu. Bankalardan biri
yavaşladığında connection pool tükenir ve tüm servis dururdu.

TX1 ile TX2 arasında uygulama ölürse ne olur? İşlem `SENDING`'de kalıyor. Mesaj Kafka'dan tekrar geliyor ama inbox
atlıyor. Recovery job 30 sn sonra işlemi `UNKNOWN` yapıyor ve inquiry akışı devreye giriyor. Ödeme kaybolmuyor.

### 3. Banka başına izolasyon

Her banka için ayrı listener container, ayrı consumer group, ayrı circuit breaker ve ayrı bulkhead var. Lokalde tüm
bankalar tek süreçte çalışıyor, production'da `BANK_CODES=YKB` ile banka başına ayrı deployment çıkılabiliyor. Kod aynı.

### 4. Circuit breaker durumu routing'e bildiriliyor

- Circuit durumu `bank.health` topic'ine gidiyor, routing-service bankayı otomatik olarak devre dışı bırakıyor ve geri açıyor.
- Operasyonun elle verdiği `active` kararı ile otomatik gelen `healthy` bilgisi ayrı kolonlarda tutuluyor. Circuit kapanınca, operasyonun kapattığı bir banka yanlışlıkla açılmıyor.
- Sadece `CLOSED` sağlıklı sayılıyor. Circuit'i kapatan deneme çağrılarını echo probu yapıyor.
- Bu event outbox'tan geçmiyor, çünkü iş verisi değil bir durum bildirimi. Mesaj kaybolursa 30 sn'lik periyodik bildirim durumu düzeltiyor.
- Bankanın reddetmesi (51, 05) başarılı çağrı sayılıyor. Aksi halde bakiyesi yetmeyen kart sahipleri bankayı devre dışı bırakırdı.

### 5. Card vault (tokenization)

Kart verisi payment-api'de şifreli tutuluyor, event'lere sadece token gidiyor, adapter bankaya göndermeden hemen önce
veriyi çözüyor, banka cevabıyla birlikte veri siliniyor.

**Alternatif: Kart verisini şifreleyip event'e koymak.** Daha basit, ama iki sorunu var. Kafka log'u retention süresi
boyunca şifreli CVV tutmuş olur; PCI DSS bunu yetkilendirme sonrasında yasaklıyor. Şifreleme anahtarı da her
consumer'a dağıtılmak zorunda kalır.

**Alternatif: Ayrı bir vault servisi (ya da HSM).** Production'da doğru olan bu. PCI kapsamı tek bir servise iner.
Şimdilik payment-api içinde, ayrı paket ve ayrı şifreleme anahtarı ile tutuluyor. Taşınması kolay.

## Sonuçlar

- (+) Gerçek ortamda "banka onayladı ama cevap gelmedi" senaryosunda bankaya tek istek gitti ve ödeme inquiry ile `APPROVED` oldu.
- (+) YKB çöktüğünde tek çekim işlemler otomatik olarak QNB'ye kaydı. YKB düzelince trafik geri döndü. Kimse elle müdahale etmedi.
- (+) Çöküş sırasında cevapsız kalan işlemler reversal ile kapandı, para çekilmedi.
- (-) Circuit açıkken routing'e haber gidene kadar (milisaniyeler) gelen işlemler `FAILED` oluyor. Bu işlemler başka bankaya yeniden yönlendirilebilir; şu an kart sahibi tekrar denemek zorunda.
- (-) Her adapter pod'unun kendi circuit breaker'ı var. Çok pod'lu ortamda pod'lar farklı durumlar bildirirse routing son gelen durumu uygular. Çözüm olarak durumlar birleştirilebilir (çoğunluk) ya da circuit durumu paylaşımlı tutulabilir (Redis).
- (-) Card vault'a erişilemezse Kafka mesajı 3 kez denenip DLT'ye gidiyor ve ödeme `ROUTED`'da kalıyor. DLT alarmı bu durumu yakalar.
