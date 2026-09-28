# ADR-001: Mesajlaşma için Kafka ve banka başına topic

- **Durum:** Kabul edildi
- **Tarih:** 28.09.2026

## Bağlam

Gelen ödemeler BIN'e göre farklı bankalara dağıtılacak. Bankaların cevap süreleri ve hata oranları birbirinden farklı.
Bir bankanın yavaşlaması ya da çökmesi diğer bankalara giden işlemleri etkilememeli. Sistem yatay ölçeklenebilmeli.

## Karar

1. Servisler arası iletişim **Kafka** üzerinden, event olarak yapılacak.
2. Routing sonrası her bankanın kendi topic'i olacak: `bank.requests.{BANK_CODE}`.
3. Mesaj key'i `paymentId` olacak. Aynı ödemenin mesajları aynı partition'a düşer ve sırası korunur.
4. `bank-adapter` tek bir kod tabanından oluşacak, her banka için ayrı deployment ve consumer group ile çalışacak.

## Değerlendirilen alternatifler

| Alternatif | Neden seçilmedi |
|---|---|
| Tek topic, key = bankCode | Tek bir bankanın trafiği tek partition'a sıkışır. Yavaş banka o partition'daki herkesi bekletir (head-of-line blocking). |
| Tek topic, adapter mesajı filtreler | Her adapter tüm trafiği okur. Kaynak israfı olur ve lag metriği bankaya göre ayrışmaz. |
| RabbitMQ (banka başına queue) | Routing için uygun, ama replay yapılamıyor ve event log olarak kullanılamıyor. Mutabakat ve audit için log saklamak istiyoruz. |
| Senkron REST zinciri | Banka timeout'u doğrudan POS'a yansır ve backpressure yönetilemez. |

## Sonuçlar

- (+) Banka bazında izolasyon sağlanır. YKB yavaşlarsa sadece `bank.requests.YKB` topic'inde lag oluşur.
- (+) Banka bazında ölçekleme yapılabilir. Yoğun bankanın partition ve pod sayısı ayrı ayrı artırılır.
- (+) Consumer lag, bankanın sağlığını gösteren doğal bir metrik olur ve routing kararında kullanılabilir.
- (-) Yeni banka eklendiğinde topic de oluşturulmalı (IaC ya da auto-create ile).
- (-) Kafka at-least-once çalışır. Consumer tarafında idempotency (inbox) **zorunlu** hale gelir. Bu konu ADR-003'te ele alınacak.
