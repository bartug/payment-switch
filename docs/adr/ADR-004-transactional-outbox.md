# ADR-004: Kafka'ya yazmak için transactional outbox

- **Durum:** Kabul edildi
- **Tarih:** 28.09.2026

## Bağlam

Ödeme oluşturulduğunda hem DB'ye yazmamız hem de routing için Kafka'ya event basmamız gerekiyor. DB ve Kafka iki ayrı sistem;
ikisini tek transaction'da atomik olarak güncelleyemiyoruz (**dual-write problemi**):

| Sıra | Ne ters gidebilir | Sonuç |
|---|---|---|
| Önce DB, sonra Kafka | Commit sonrası uygulama ölür ya da Kafka kapalıdır | Ödeme kayıtlı ama bankaya hiç gitmez, sonsuza kadar `PENDING` kalır |
| Önce Kafka, sonra DB | Mesaj gider, DB commit'i başarısız olur | Var olmayan bir ödeme bankaya gider ve kart sahibinden para çekilir |
| `@Transactional` içinde Kafka'ya yazmak | Kafka send'i DB rollback'ine dahil olmaz | İlk satırla aynı sorun |

## Karar

Event, Kafka yerine ödemeyle **aynı DB transaction**'ında `outbox_event` tablosuna yazılır. Ayrı bir relay tabloyu
okuyup Kafka'ya basar.

```
TX { payment INSERT, idempotency_record INSERT, outbox_event INSERT }  ← atomik
                                     │
OutboxRelay (200 ms'de bir):         ▼
TX { SELECT ... FOR UPDATE SKIP LOCKED LIMIT 100 → Kafka send → onay bekle → published_at = now }
```

### Detaylar

1. **Polling + `FOR UPDATE SKIP LOCKED`.** Birden fazla pod aynı anda relay çalıştırabilir. Bir pod'un kilitlediği satırları diğeri atlar, aynı event iki pod tarafından alınmaz. Bu durum testte kendiliğinden oluştu: cache'lenmiş iki Spring context'in relay'i aynı DB'de çalıştı ve satırı paylaşmadılar.
2. **At-least-once.** Kafka onay verdikten sonra commit'ten önce uygulama ölürse satır gönderilmemiş kalır ve tekrar gönderilir. Exactly-once teslimat yok. Consumer'lar `event-id` header'ı ile tekrar eden mesajları ayıklamak zorunda (inbox, PS-5).
3. **Mesaj key'i = `paymentId`.** Aynı ödemenin event'leri aynı partition'a düşer ve sırayla işlenir.
4. **Producer ayarları:** `acks=all` ve `enable.idempotence=true`. Broker onayı kaybolup producer retry yaparsa broker mesajı iki kez yazmaz.
5. **Kafka kapalıysa hızlı vazgeçiliyor.** `max.block.ms=3s`. Batch içinde ilk senkron hatada kalan mesajlar gönderilmeden bırakılıyor. Aksi halde 100 kayıtlık bir batch DB kilidini 100 × 3 sn tutardı.
6. **Kart numarası event'e yazılmıyor.** Sadece BIN ve son 4 hane gidiyor. Kafka log'u PCI kapsamına girmiyor.
7. **Gönderilmiş kayıtlar 7 gün saklanıyor.** Olay incelemesi ve gerekirse yeniden gönderim için. Sonra 1000'erli parçalar halinde siliniyor.

### Değerlendirilen alternatifler

| Alternatif | Neden şimdilik seçilmedi |
|---|---|
| **Debezium (CDC)** | Postgres WAL'ını okuyup Kafka'ya basar. Polling gecikmesi yok ve DB'ye sorgu yükü bindirmez. Ama Kafka Connect cluster'ı ve replication slot yönetimi operasyonel yük getiriyor. Hacim arttığında geçiş yapılabilir; tablo yapısı Debezium'un outbox event router'ı ile uyumlu. |
| **Kafka transaction'ları** | Sadece Kafka ↔ Kafka arasında exactly-once sağlar. DB'yi kapsamaz. |
| **`@TransactionalEventListener(AFTER_COMMIT)`** | Commit sonrası uygulama ölürse event kaybolur. Dual-write sorununu çözmüyor, sadece erteliyor. |

## Sonuçlar

- (+) Kafka kapalıyken ödeme almaya devam ediliyor. Event'ler birikiyor, Kafka gelince gönderiliyor. Bu gerçek ortamda da denendi.
- (+) `outbox_pending_events` ve `outbox_oldest_pending_seconds` metrikleri alarm için doğrudan kullanılabilir.
- (-) Polling aralığı kadar (≤200 ms) ek gecikme var.
- (-) Batch'te bir event başarısız olup aynı key'e sahip sonraki bir event başarılı olursa sıra bozulabilir. Şu an ödeme başına tek event olduğu için sorun değil. Ödeme başına birden fazla event üretildiğinde (PS-6), aynı key'in bekleyen event'i varsa sonrakiler gönderilmemeli.
- (-) Her Prometheus scrape'inde iki küçük sorgu çalışıyor. Partial index sayesinde bu sorgular gönderilmiş kayıt sayısından etkilenmiyor.
