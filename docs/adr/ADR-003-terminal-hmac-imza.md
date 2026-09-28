# ADR-003: Terminal kimlik doğrulama için HMAC imza

- **Durum:** Kabul edildi
- **Tarih:** 28.09.2026

## Bağlam

Ödeme isteği gönderen terminalin gerçekten o terminal olduğundan, isteğin yolda değiştirilmediğinden ve yakalanan bir
isteğin tekrar gönderilemeyeceğinden emin olmamız gerekiyor. Terminaller sahada çalışan, zaman zaman bağlantısı kopan
cihazlar. Kullanıcı girişi veya OAuth akışı yok.

## Karar

Her terminalin kendine ait bir secret'ı olacak. İstekler `HMAC-SHA256(secret, stringToSign)` ile imzalanacak.
Algoritmanın detayı `docs/03-terminal-kimlik-dogrulama.md` dokümanında.

### Değerlendirilen alternatifler

| Alternatif | Neden seçilmedi |
|---|---|
| Statik API key (header'da) | Key yakalanırsa her istek taklit edilebilir. Body'nin değiştirilmediği garanti edilemez. |
| OAuth2 client credentials + JWT | Token yenileme akışı cihaz tarafında karmaşık, token yakalanırsa süresi dolana kadar geçerli. Body bütünlüğünü de sağlamıyor. |
| mTLS | En güçlü seçenek, fiziki POS için ileride düşünülebilir. Sertifika dağıtımı ve rotasyonu operasyonel yük getiriyor. HMAC ile birlikte de kullanılabilir. |

### Detay kararlar

1. **Idempotency-Key imzaya dahil.** Saldırgan yakalanan isteğin key'ini değiştirip idempotency'yi atlatarak yeni ödeme oluşturamaz.
2. **Ayrı bir nonce deposu yok.** Replay koruması iki katmandan oluşuyor: ±5 dk zaman penceresi ve pencere içindeki tekrarlar için idempotency (aynı key ile yeni ödeme oluşmuyor). Redis'te nonce tutmak ek bir bağımlılık ve Redis çöktüğünde ne olacağı sorusunu da beraberinde getirirdi.
3. **Üye işyeri bilgisi body'den değil terminalden geliyor.** Terminal başka bir üye işyeri adına ödeme alamaz. `GET` isteğinde de sadece kendi üye işyerinin ödemesini görebilir, başkasınınki için 404 döner (IDOR).
4. **Terminal yok ile imza hatalı aynı cevabı alıyor.** Terminal numarası taraması yapılamaz. Pasif terminal kontrolü imzadan **sonra** yapılıyor; aksi halde pasif terminaller dışarıdan tespit edilebilirdi.
5. **Aynı header iki kez gelirse istek reddediliyor.** Filter ilk değeri, Spring MVC ise değerlerin birleşimini okuyor. İmzalanan ile işlenen key farklılaşabilir (header smuggling). Bu açığı test yazarken bulduk.
6. **Secret DB'de AES-256-GCM ile şifreli.** HMAC doğrulaması için secret'ın kendisi gerektiğinden hash'lenemez. Master key env'den geliyor, production'da KMS/Vault'tan gelmeli. Ciphertext'teki `v1:` ön eki anahtar rotasyonu için.
7. **İmzalar sabit sürede karşılaştırılıyor** (`MessageDigest.isEqual`, timing attack).

## Sonuçlar

- (+) Body bütünlüğü, terminal kimliği ve replay koruması tek bir mekanizmayla sağlanıyor.
- (+) Terminal SDK'sı sadece HMAC-SHA256 ve SHA-256 gerektiriyor. Her dilde ve cihazda mevcut (`scripts/pos-request.sh` openssl ile yazıldı).
- (-) Terminal saati bozuksa (±5 dk) işlem alınamaz. Fiziki POS'larda NTP senkronizasyonu şart.
- (-) Her istekte terminal DB'den okunup secret çözülüyor. Yük artınca kısa TTL'li bir cache (Caffeine) eklenecek. Cache süresi, pasife alınan bir terminalin en fazla ne kadar süre daha işlem gönderebileceğini de belirleyecek.
- (-) `/v1/admin/terminals` şu an korumasız. Backoffice kimlik doğrulaması ile korunmalı.
