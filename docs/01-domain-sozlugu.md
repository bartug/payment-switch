# Ödeme Sistemleri Domain Sözlüğü

Bu doküman projede geçen kavramları kendi notlarım olarak topladığım yer. Param ve MoneyPay entegrasyonlarında
"API'ye gönder, cevabı al" tarafındaydık; burada o API'nin arkasında dönenleri yazıyoruz.

## 1. Taraflar

| Kavram | Açıklama |
|---|---|
| **Kart sahibi (Cardholder)** | Kartı kullanan kişi. |
| **Üye işyeri (Merchant)** | Ödemeyi kabul eden işletme. Bizim senaryoda taksici / işletme. |
| **Issuer (Kartı çıkaran banka)** | Kartı basan ve limitten parayı düşen banka. Onay/red kararını **issuer verir**. |
| **Acquirer (Üye işyeri bankası)** | Üye işyerine POS'u veren banka. Parayı üye işyerine öder (settlement). |
| **Scheme / Kart kuruluşu** | Visa, Mastercard, Troy. Kuralları koyar, bankalar arası mesajı taşır. |
| **BKM** | Türkiye'de yurtiçi bankalar arası (off-us) işlemlerin switch'i ve takas merkezi. |
| **Payment Facilitator / Aggregator** | Param, MoneyPay, iyzico gibi. Birden fazla bankanın POS'unu tek API'nin arkasına koyar. **Bu projede yazdığımız şey bu katman.** |
| **Payment Switch** | Gelen işlemi doğru hedefe (banka) yönlendiren sistem. |

## 2. On-us / Off-us

- **On-us:** Kartın bankası = POS'un bankası. Örneğin Yapı Kredi kartı Yapı Kredi POS'unda kullanılıyor. İşlem bankanın içinde
  kalır, BKM'ye gitmez. Hem daha hızlıdır hem komisyonu daha düşüktür.
- **Off-us:** Kart ve POS farklı bankalara ait. İşlem acquirer → BKM/scheme → issuer yolunu izler ve interchange ücreti oluşur.

Aggregator'ların BIN'e bakıp işlemi kartın kendi bankasına yönlendirmesinin iki sebebi var:
1. **Taksit.** Taksit programları banka bazlıdır (aşağıda). Taksitli işlem, kartın ait olduğu programın bankasının POS'undan geçmek zorunda.
2. **Maliyet.** On-us işlemin komisyonu daha düşük olduğu için tek çekimde bile tercih edilir.

## 3. BIN (Bank Identification Number)

- Kart numarasının ilk **6 veya 8 hanesi**. Yeni kartlarda 8 haneli BIN standartlaşıyor.
- BIN'den öğrenilenler: issuer banka, kart kuruluşu (Visa/MC/Troy), kart tipi (kredi/banka/ön ödemeli), ticari/bireysel ayrımı ve taksit programı.
- Çözümleme **en uzun prefix eşleşmesi** ile yapılır. Önce 8 haneye, bulunamazsa 6 haneye bakılır.
- Projede örnek bir BIN tablosu kullanıyoruz, gerçek BIN listesi değil.

### Taksit programları

| Program | Banka |
|---|---|
| World | Yapı Kredi |
| Bonus | Garanti BBVA |
| Axess | Akbank |
| CardFinans | QNB Finansbank |
| Maximum | Türkiye İş Bankası |

Programların ortak kart anlaşmaları da var, yani başka bankaların kartları da bir programa dahil olabiliyor. Bu yüzden BIN tablosunda
"banka" ile "program" ayrı kolonlarda tutuluyor.

## 4. İşlem tipleri

| İşlem | Ne zaman | Not |
|---|---|---|
| **Satış (Sale)** | Tek adımda çekim. | En yaygın işlem. |
| **Provizyon (Pre-auth / Auth)** | Limitte bloke koyar, para çekilmez. | Otel ve araç kiralamada kullanılır. |
| **Provizyon kapama (Capture)** | Bloke edilen tutarın çekilmesi. | Tutar provizyon tutarına eşit veya ondan düşük olabilir. |
| **İptal (Void)** | Gün sonu **öncesi** işlemin iptali. | Takasa hiç girmez. |
| **İade (Refund)** | Gün sonu **sonrası** geri ödeme. | Yeni bir finansal işlemdir, kısmi yapılabilir. |
| **Teknik iptal (Reversal)** | Cevabı alınamayan işlemin sistem tarafından geri alınması. | Timeout durumunda kullanılır. **En kritik akış bu.** |

> Mülakat: "Void ile refund arasındaki fark ne?" Void gün sonundan önce yapılır ve işlem takasa hiç girmez.
> Refund gün sonundan sonra yapılır ve takasa ayrı bir işlem olarak girer.

## 5. ISO 8583 (kısa)

Bankalar arası kart mesajlaşma standardı. Bankaların sanal POS API'leri genelde XML veya JSON olsa da içerikleri bu mesajlara karşılık gelir.

| MTI | Anlam |
|---|---|
| 0100 / 0110 | Yetkilendirme isteği / cevabı |
| 0200 / 0210 | Finansal işlem isteği / cevabı |
| 0400 / 0410 | Reversal isteği / cevabı |
| 0800 / 0810 | Ağ yönetimi (echo, sign-on) |

Önemli alanlar: **DE2** PAN, **DE4** tutar, **DE11** STAN (terminal sıra no), **DE37** RRN (referans no), **DE38** onay kodu,
**DE39** cevap kodu. En sık cevap kodları: `00` onay, `05` onaylanmadı, `51` yetersiz bakiye, `54` süresi dolmuş kart,
`91` issuer ulaşılamıyor.

## 6. Fiziki POS ve sanal POS

| | Fiziki POS (card-present) | Sanal POS (card-not-present) |
|---|---|---|
| Doğrulama | EMV çip + PIN | 3D Secure (SMS / bank app) |
| Risk | Düşük | Daha yüksek (chargeback) |
| Veri | Çipten kriptogram gelir | Kart no + SKT + CVV |

**3D Secure akışı:** Merchant → MPI → ACS (issuer'ın doğrulama sunucusu) → kart sahibi doğrular → `ECI` ve `CAVV`
değerleri döner → bu değerlerle provizyon istenir.

## 7. Gün sonu, takas ve mutabakat

- **Gün sonu (batch close):** POS o günün işlemlerini kapatır.
- **Takas (Clearing):** Bankalar arası işlem kayıtlarının karşılaştırılması.
- **Settlement / valör:** Paranın üye işyeri hesabına geçmesi. Örneğin "T+1" veya "blokeli 30 gün".
- **Mutabakat (Reconciliation):** Bizim kayıtlarımız ile bankanın gün sonu dosyasının karşılaştırılması.
  Eşleşmeyen işlem, para kaybı ya da çift çekim riski demektir.

## 8. Ücretler

- **MDR (Merchant Discount Rate):** Üye işyerinden kesilen komisyon.
- **Interchange:** Off-us işlemde acquirer'ın issuer'a ödediği ücret.
- **Taksit komisyonu:** Taksit sayısı arttıkça artar. Routing kararlarında bu değer maliyet kuralına girdi olarak kullanılır.

## 9. PCI DSS (dikkat edilecekler)

- CVV **hiçbir koşulda** saklanmaz, log'a da yazılmaz.
- PAN açık halde saklanmaz. Sadece BIN + son 4 hane tutulur, gerekirse tokenize edilir.
- Log'larda kart numarası maskelenir: `454671******1234`.
