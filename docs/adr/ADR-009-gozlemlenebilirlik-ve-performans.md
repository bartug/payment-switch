# ADR-009: Gözlemlenebilirlik ve yük testinden çıkan performans kararları

- **Durum:** Kabul edildi
- **Tarih:** 30.09.2026

## Bağlam

Dört servisli, asenkron bir akışta "bu ödeme neden 3 saniye sürdü?" sorusu log'larla cevaplanamıyor. Ayrıca
sistemin kaç TPS alabildiği ve darboğazın nerede olduğu ölçülmeden ölçekleme kararı verilemez.

## Kararlar

1. **Micrometer Observation + OpenTelemetry (OTLP) + Jaeger.** Spring'in kendi soyutlaması kullanılıyor; HTTP server, HTTP client, Kafka producer ve consumer span'ları otomatik oluşuyor. OTel Java agent seçilmedi; agent bytecode değiştiriyor, sürüm uyumsuzluklarında sorun çıkarıyor, ayrıca Spring'in observation'ı zaten yeterli.
2. **Trace context outbox satırında saklanıyor.** Relay sonradan ve başka bir thread'de gönderdiği için trace aksi halde kopardı. Satırdaki `traceparent`'tan bir span açılıyor, KafkaTemplate bunun altında producer span'ını oluşturuyor.
3. **İş metriği: uçtan uca süre.** API'nin 202 dönme süresi kullanıcının beklediği süre değil. `payment_end_to_end_seconds` histogram'ı SLO ve alarm için kullanılıyor.
4. **After-commit nudge + polling.** Yük testinde sürenin %70'inin outbox polling'inde geçtiği ölçüldü. Commit sonrası relay hemen tetikleniyor; polling yedek olarak kalıyor. Sonuç: uçtan uca p50 0,78 sn'den 0,15 sn'ye indi.
5. **Consumer concurrency ve DB havuzu birlikte ayarlanıyor.** Varsayılan 1 thread lag biriktirdi, 6 thread DB havuzunu tüketti. payment-api için 3 thread seçildi. Kural: aynı anda DB tutan thread sayısı havuz boyutundan küçük olmalı.
6. **Sampling config'den.** Lokalde %100; production'da %1–10 (`TRACING_SAMPLING`). Hata olan trace'leri her zaman tutmak için tail-based sampling (OTel Collector) sonraki adım.

## Sonuçlar

- (+) Tek bir ödeme Jaeger'da 4 servis ve 17 span olarak görülüyor; her adımın süresi ve bekleme ölçülebiliyor.
- (+) 100 TPS'de kararlı durumda uçtan uca p50 0,15 sn, p95 ~0,3 sn; API p99 < 250 ms, hata %0.
- (+) 250 TPS'de sistem doyuyor ama hiçbir ödeme kaybolmuyor. Birikme Kafka'da eriyor ve tüm ödemeler sonuçlanıyor.
- (-) API'de backpressure yok. İşleme kapasitesi aşılınca istek kabul edilmeye devam ediyor ve uçtan uca süre uzuyor. Lag belirli bir eşiği geçerse 429/503 dönmek (load shedding) düşünülmeli.
- (-) Ölçümler tek bir laptop'ta; mutlak değerler değil, darboğazların sırası ve değişikliklerin etkisi anlamlı.
