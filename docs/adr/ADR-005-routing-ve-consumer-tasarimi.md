# ADR-005: Routing kural zinciri ve güvenilir consumer tasarımı

- **Durum:** Kabul edildi
- **Tarih:** 28.09.2026

## Bağlam

routing-service hem consume hem produce eden ilk servis. Bu servisin:
- aynı ödemeyi iki kez bankaya göndermemesi,
- karar ile Kafka'ya gönderimi atomik yapması,
- bozuk mesaj yüzünden partition'ı durdurmaması,
- yeni kural eklemeyi kolay tutması gerekiyor.

## Kararlar

### 1. Kural zinciri (Chain of Responsibility)

Her kural `Optional<RoutingResult> evaluate(RoutingContext)` metodunu uyguluyor. Karar veren ilk kural zinciri bitiriyor.
Kurallar saf Java: DB, Kafka ve cache'e erişmiyorlar. Gereken her şey `RoutingContext` içinde veriliyor. Bu sayede:
- kurallar Spring'siz test ediliyor (`RoutingEngineTest`, 12 senaryo, 30 ms),
- simülasyon uç noktası gerçek akışla birebir aynı kodu çalıştırıyor,
- yeni kural sadece yeni bir sınıf demek; mevcut kurallara dokunulmuyor (open/closed).

**Alternatif: Kural motoru (Drools, DB'de tutulan kurallar).** Kurallar sık değişmiyor ve değiştiğinde test edilmesi
gerekiyor. Kod içinde tutmak daha güvenli. Komisyon oranları gibi sık değişen *veriler* DB'de tutuluyor.

### 2. Idempotent consumer: inbox

```
TX {
  INSERT processed_event (event_id, consumer) ON CONFLICT DO NOTHING   → 0 satır ise çık
  INSERT routing_decision (payment_id UNIQUE)
  INSERT outbox_event × 2
}
```

- Offset, listener başarıyla döndükten **sonra** commit ediliyor. Arada uygulama çökerse mesaj tekrar geliyor, inbox bu tekrarı yakalıyor.
- Inbox kaydı iş verisiyle aynı transaction'da yazılıyor. İşlem rollback olursa inbox kaydı da geri alınıyor ve mesaj tekrar geldiğinde yeniden işleniyor.
- `routing_decision.payment_id` üzerinde ayrıca unique constraint var. Aynı ödeme farklı bir `event-id` ile gelse bile (örneğin outbox'tan elle yeniden gönderim) ikinci karar yazılamıyor.
- `ON CONFLICT DO NOTHING` tek sorguda atomik. Önce `SELECT` sonra `INSERT` yapmak iki consumer arasında yarışa açık olurdu.

### 3. Hata yönetimi: retry ve DLT

- Geçici hatalar (DB bağlantısı, deadlock) 1 sn, 2 sn, 4 sn arayla tekrar deneniyor. Hâlâ başarısızsa mesaj `<topic>.DLT`'ye gidiyor, partition kalan mesajlarla devam ediyor.
- Bozuk mesajlar (JSON okunamıyor, `event-id` yok) `InvalidEventException` fırlatıyor ve retry yapılmadan doğrudan DLT'ye gidiyor.
- DLT'ye giden her mesaj sebebiyle birlikte ERROR olarak log'lanıyor.
- DLT adı açıkça `.DLT` olarak veriliyor. Spring Kafka 3.3 varsayılanı `-dlt` yaptı, bunu test yazarken fark ettik.

### 4. Ortak `messaging` modülü

Outbox, inbox, event okuma ve hata yönetimi bir Spring Boot auto-configuration modülüne taşındı. Servis bu modülü
bağımlılık olarak ekliyor ve iki tabloyu kendi migration'ına koyuyor. Sınıflar `@Component` değil, bean'ler
auto-configuration'da tanımlanıyor. Tüm servisler kök paketi taradığı için aksi halde bu modülü kullanmayan servislerde de bean oluşmaya çalışırdı.

### 5. Servis başına şema

Her servis aynı Postgres sunucusunda kendi şemasının sahibi (`public` → payment-api, `routing` → routing-service).
Servisler birbirinin tablosuna erişmiyor, veri sadece event'lerle geçiyor. Bağlantının şeması Hikari `schema` ayarıyla
veriliyor. URL parametresi (`currentSchema`) Testcontainers ya da secret'lar URL'i override ettiğinde kayboluyordu; bunu da test yazarken bulduk.

### 6. Topic'i consumer da tanımlar

Consumer, olmayan bir topic'e abone olursa topic sonradan oluşsa bile metadata yenilenene kadar (5 dk) mesaj almıyor.
Bu yüzden her servis ürettiği topic'lerle birlikte tükettiği topic'leri ve onların DLT'lerini de `NewTopic` olarak tanımlıyor.
Topic zaten varsa tanım bir şey yapmıyor.

### 7. Cache

| Veri | Cache | Süre | Neden |
|---|---|---|---|
| BIN çözümleme | Caffeine, 100k kayıt | 1 saat | Her ödemede okunuyor, nadiren değişiyor. Tanımsız BIN'ler de cache'leniyor. |
| Aktif bankalar | Caffeine | 5 sn | Bir pod'da pasife alınan banka diğer pod'lara en geç 5 sn'de yansıyor. Güncelleme yapılan pod'da commit sonrası hemen yansıyor. |

## Sonuçlar

- (+) Aynı event iki kez geldiğinde tek karar veriliyor ve bankaya tek istek gidiyor (`ayniEventIkiKezGelirseTekKararVerilir`).
- (+) Bozuk bir mesaj partition'ı durdurmuyor (`bozukMesajDltyeGider`).
- (-) Banka pasife alındığında diğer pod'larda 5 sn boyunca eski durum geçerli. O sürede o bankaya giden işlemler bank-adapter'da circuit breaker'a takılacak (PS-5).
- (-) Banka durumu şu an sadece elle değiştiriliyor. PS-5'te bank-adapter'ın circuit breaker durumu event olarak yayınlanacak ve routing bunu otomatik kullanacak.
- (-) `payment.routing.results` ile ileride gelecek banka sonucu farklı topic'lerde olacak, aralarında sıra garantisi yok. payment-api, ödeme `PENDING` durumundan çıkmışsa routing sonucunu yok sayıyor.
