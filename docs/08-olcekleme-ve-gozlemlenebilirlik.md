# Ölçekleme ve Gözlemlenebilirlik

## 1. Gözlemlenebilirlik

```bash
docker compose --profile observability up -d
```

| Araç | Adres | Ne için |
|---|---|---|
| Jaeger | http://localhost:16686 | Tek bir ödemenin dört servis boyunca izlenmesi (trace) |
| Prometheus | http://localhost:9090 | Metrikler ve alarm kuralları (`infra/prometheus/alerts.yml`) |
| Grafana | http://localhost:3000 | "Payment Switch" dashboard'u, 14 panel |

### Trace'in outbox'tan geçirilmesi

HTTP isteğinin trace context'i istek thread'ine bağlı. Outbox relay ise event'i başka bir thread'de, sonradan
gönderiyor; o anda trace context yok. Hiçbir önlem alınmasaydı trace outbox'ta kopardı.

```
HTTP isteği (trace A) ─► TX { payment + outbox_event(trace_parent = "00-A-span-01") }
                                          │
Relay (başka thread) ◄────────────────────┘  trace_parent'tan span aç → KafkaTemplate send
                                                     │  traceparent header'ı
routing-service listener (trace A devam) ◄───────────┘
```

Bir ödemenin Jaeger'da görünen tam trace'i (17 span, 4 servis):

```
+  0 ms   payment-api      http post /v1/payments                   12.7 ms
+175 ms   payment-api      outbox publish payment.requested          ← outbox'ta bekleme
+225 ms   routing-service  payment.requested receive                10.8 ms
+468 ms   routing-service  outbox publish bank.requests.QNB          ← outbox'ta bekleme
+555 ms   bank-adapter     bank.requests.QNB receive                22.1 ms
+558 ms   payment-api      http post /internal/v1/card-vault/...     4.3 ms
+570 ms   bank-simulator   http post /banks/{bankCode}/v1/authorize  0.5 ms
+797 ms   bank-adapter     outbox publish payment.bank.results       ← outbox'ta bekleme
+976 ms   payment-api      payment.bank.results receive               4.8 ms
```

### Temel metrik: uçtan uca süre

`payment_end_to_end_seconds`, POS'un isteği ile ödemenin sonuçlanması arasındaki süre. API'nin cevap süresi (202)
yanıltıcı; kullanıcının asıl beklediği süre bu. SLO bu metriğe kurulmalı.

## 2. Yük testi

```bash
./mvnw -Pload-test -pl load-test gatling:test -Drps=100 -Dduration=60 -Dterminals=20
```

Test, istekleri gerçek bir POS gibi HMAC ile imzalıyor. İmzalama kodu, dokümana bakılarak bağımsız yazıldı;
payment-api'nin kodunu kullanmıyor. Ortam: MacBook, 4 servis JVM'i lokalde, Postgres, Kafka, Redis ve observability Docker'da.

### Ölçümler ve bulunan darboğazlar

| # | Değişiklik | TPS | API p99 | Uçtan uca p50 | Uçtan uca p95 | Bulgu |
|---|---|---|---|---|---|---|
| 1 | Başlangıç (polling 200 ms) | 100 | 47 ms | 0,78 sn | 1,7 sn | Trace: sürenin **%70'i 3 outbox'ta polling beklemesi** |
| 2 | Polling 50 ms | 100 | 64 ms | **8,4 sn** | 22 sn | Kötüleşti! payment-api consumer lag 201 |
| 3 | payment-api consumer concurrency 1 → 6 | 100 | 270 ms | 0,78 sn | 3,1 sn | Lag gitti, ama **DB havuzu doldu** (20/20, bekleyen var) |
| 4 | Concurrency 3 + **after-commit nudge** | 100 | 224 ms | **0,15 sn** | 0,3 sn* | p50 5 kat iyileşti |
| 5 | Aynı ayarlar | 250 | 1,1 sn | - | - | Doygunluk: DB havuzunda 243 bekleyen, routing lag 1076 |

\* Kararlı durumda. Yükün 100 TPS'e çıktığı ilk ~40 sn'de p95 anlık olarak 4 sn'ye çıkıyor (JIT, havuzların dolması, GC).
Bu yüzden ölçümden önce bir ısınma turu yapılmalı.

**Deneme 2'nin dersi:** Polling'i hızlandırmak gecikmeyi azaltmak yerine 10 kat artırdı. Asıl darboğaz polling değil,
payment-api'nin varsayılan 1 thread'li consumer'larıydı. Relay'ler hızlanınca mesajlar daha yoğun dalgalar halinde
geldi ve tek thread'li consumer yetişemedi. Ölçmeden yapılan "optimizasyon" asıl sorunu gizleyebilir, hatta kötüleştirebilir.

**Deneme 3'ün dersi (Little yasası):** Aynı anda DB bağlantısı tutan thread sayısı havuz boyutunu aşarsa istekler
bağlantı için kuyruğa girer. Virtual thread'ler aynı anda gelen istek sayısını sınırlamıyor; sınırlayıcı artık DB havuzu.
İki listener × 6 thread + HTTP + relay + webhook, 20'lik havuzu doldurdu.

**Deneme 4: after-commit nudge.** Outbox'a yazan transaction commit olunca relay hemen uyandırılıyor. Arka arkaya gelen
commit'ler tek çalışmada birleştiriliyor. Polling yedek olarak kalıyor: başka pod'un commit'i, Kafka'nın kapalı olduğu
anlar ve pod'un uyandırma sırasında ölmesi için.

**Deneme 5: kabul hızı ≠ işleme hızı.** API saniyede 250 ödeme kabul etti, hiç hata vermedi. Ama arka plandaki akış
saniyede ~140 ödeme işleyebildi (Grafana'da banka sonuçları banka başına ~28/sn'de tavan yaptı). Fark Kafka'da lag
olarak birikti. Yük bitince lag eridi ve **15.553 ödemenin tamamı `APPROVED` oldu**; kaybolan ya da takılan ödeme yok.
Birikme sırasında uçtan uca süre 96 sn'ye kadar çıktı.

### Beklenmeyen bir yan bulgu: laptop uykusu ve kart verisi TTL'i

Bir test sırasında laptop uyku moduna geçti. macOS her ~15 dakikada bir kısa süreliğine uyanınca (DarkWake) Gatling
birkaç yüz istek gönderebildi. Ödemeler kabul edildi, ama akış tamamlanmadan laptop tekrar uyudu. Bir sonraki uyanışta
kart verisinin 15 dakikalık TTL'i dolmuştu ve bank-adapter ödemeyi **bankaya göndermeden** `FAILED` yaptı.
~5600 ödeme bu şekilde sonuçlandı. Sistem güvenli tarafta kaldı: kart verisi olmadan bankaya gidilmedi, para çekilmedi.
Bu, production'daki "pod'lar uzun süre duraksadı" durumunun iyi bir simülasyonu. Ölçümler bu koşu hariç tutularak verildi.

## 3. Ölçekleme

### Darboğaz sırası (bu ortamda)

1. **payment-api DB bağlantı havuzu.** 250 TPS'de havuzda 243 bekleyen oldu.
2. **routing-service consumer'ları.** 3 thread ile ~140 mesaj/sn işlenebildi.
3. **Outbox relay.** Nudge ile çözüldü; polling tek başına üç hop'ta ~650 ms ekliyordu.

### Nasıl ölçeklenir

| Katman | Yöntem | Sınır |
|---|---|---|
| payment-api (HTTP) | Yatay: pod sayısı | Toplam DB bağlantısı: pod × havuz < `max_connections`. Aşılırsa PgBouncer (transaction pooling). |
| Consumer'lar | Pod × concurrency ≤ partition sayısı | Partition sayısı consumer paralelliğinin **üst sınırı**. 6 partition'da 7. consumer boşta kalır. |
| Partition sayısı | Artırılabilir, **azaltılamaz** | Artırınca key → partition eşlemesi değişir; geçiş anında aynı ödemenin eski ve yeni mesajları farklı partition'lara düşebilir. Baştan hedef yükün 2-3 katına göre seçilmeli. |
| bank-adapter | Banka başına deployment (`BANK_CODES=YKB`) | Yoğun banka ayrıca ölçeklenir. Bankanın kendi TPS limiti bulkhead ile korunur. |
| Outbox relay | Her pod'da çalışır, `SKIP LOCKED` ile paylaşır | Çok yüksek hacimde polling yerine Debezium (CDC) |
| Postgres | Önce okuma replikası (GET, mutabakat), sonra servis başına ayrı DB | Şemalar zaten ayrı, veri sadece event'lerle geçiyor; ayrılması kolay |

### Kapasite hesabı (örnek)

Hedef 1000 TPS, bir ödeme payment-api'de ~3 transaction (oluşturma, routing sonucu, banka sonucu), transaction başına
ortalama ~10 ms DB süresi:

```
Gereken eş zamanlı bağlantı ≈ 1000 × 3 × 0,010 sn = 30 bağlantı   (Little yasası: L = λ × W)
Pod başına havuz 10 → en az 3 pod, pay bırakarak 5 pod
Consumer: 1000 mesaj/sn, thread başına ~150 mesaj/sn → ~7 thread → partition ≥ 12 (büyüme payı ile)
```
