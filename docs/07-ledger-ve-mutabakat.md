# Ledger ve Mutabakat

## 1. Ledger: çift taraflı kayıt

Her para hareketi, satırlarının **borç toplamı alacak toplamına eşit** olan bir yevmiye kaydıdır. Kayıtlar ödeme
durum değişikliğiyle aynı transaction'da yazılır: ödeme onaylandıysa muhasebe kaydı kesin vardır.

### Hesap planı (aggregator bakış açısıyla)

| Hesap | Tip | Anlamı |
|---|---|---|
| `BANK_RECEIVABLE:{BANKA}` | Varlık | Bankanın takas sonrası bize ödeyeceği para |
| `MERCHANT_PAYABLE:{İŞYERİ}` | Borç | Bizim üye işyerine ödeyeceğimiz para |
| `FEE_REVENUE` | Gelir | Üye işyerinden kesilen komisyon (varsayılan %2,49) |

### Kayıtlar

1000 TL satış, komisyon %2,49 (24,90 TL):

```
SALE     Borç   BANK_RECEIVABLE:YKB       1000,00
         Alacak MERCHANT_PAYABLE:MRC       975,10
         Alacak FEE_REVENUE                 24,90

REFUND   Borç   MERCHANT_PAYABLE:MRC       300,00      (komisyon iade edilmez)
(300 TL) Alacak BANK_RECEIVABLE:YKB        300,00

VOID     Borç   MERCHANT_PAYABLE:MRC       975,10      (satış kaydının ters kaydı, komisyon dahil)
         Borç   FEE_REVENUE                 24,90
         Alacak BANK_RECEIVABLE:YKB       1000,00
```

### Kurallar

- **Kayıt silinmez, güncellenmez.** Hata ya da iptal, yeni bir ters kayıtla (storno) düzeltilir. Geçmiş her zaman izlenebilir.
- **Bakiye kolonu yok.** Bakiye her zaman satırlardan hesaplanıyor. Ayrı bir bakiye kolonu, satırlarla tutarsız kalabilirdi.
- **Borç = alacak kuralını veritabanı zorluyor.** `DEFERRABLE INITIALLY DEFERRED` bir constraint trigger commit anında her kaydı kontrol ediyor. Uygulamayı atlayıp doğrudan SQL ile yazılan dengesiz bir kayıt bile commit edilemiyor; bu durum test edildi.
- **Aynı olay iki kez muhasebeleşmiyor.** `(entry_type, reference_id)` unique.
- **Yuvarlama:** Komisyon kuruşa `HALF_UP` ile yuvarlanıyor, üye işyeri payı "tutar - komisyon" olarak hesaplanıyor. Böylece yuvarlama farkı kaydı dengesiz bırakmıyor.

```bash
curl -s localhost:8081/v1/admin/ledger/payments/<paymentId>
curl -s "localhost:8081/v1/admin/ledger/balances?account=MERCHANT_PAYABLE:MRC0000001"
curl -s localhost:8081/v1/admin/ledger/trial-balance      # balanced=false ise para oluşmuş ya da kaybolmuş
```

## 2. Mutabakat

Bankanın gün sonu dosyası (CSV: `order_id,type,amount,currency,rrn,auth_code,operation_id,transaction_time`) ile
bizim kayıtlarımız **iki yönlü** karşılaştırılıyor.

```
Bizden bankaya: o gün onayladığımız satışlar ve başarılı iadelerimiz bankanın dosyasında var mı, tutarı aynı mı?
                bulunamazsa önceki / sonraki günün dosyasına da bak (gün sonu kayması)

Bankadan bize:  bankanın dosyasındaki her satırın bizde karşılığı var mı?
                yoksa → MISSING_IN_OURS; var ama başarısız / iptal / cevapsız → STATUS_MISMATCH
```

| Sonuç | Anlamı | Risk | Aksiyon |
|---|---|---|---|
| `MATCHED` | İki taraf aynı | - | - |
| `MATCHED_DIFFERENT_DAY` | Gün sonu saatine yakın işlem bankada komşu güne yazılmış | - | Bilgi |
| `MISSING_IN_BANK` | Bizde onaylı, bankada yok | Gelmeyecek para üye işyerine ödenir | Üye işyeri ödemesini (payout) durdur, bankayla teyit et |
| `MISSING_IN_OURS` | Bankada var, bizde hiç kayıt yok | Kart sahibinden para çekilmiş, kimsenin haberi yok | İade ya da manuel kayıt |
| `AMOUNT_MISMATCH` | Tutarlar farklı | Hesap kayması | İnceleme, bankaya itiraz |
| `STATUS_MISMATCH` | Bizde başarısız / iptal / cevapsız, bankada başarılı | **Çift çekimin tipik sebebi**: kart sahibi başarısız sanıp tekrar ödemiştir | Otomatik iade ya da kaydı düzelt, üye işyerini bilgilendir |

`STATUS_MISMATCH`, önceki fazların son güvenlik ağı. Örneğin reversal da başarısız olup `MANUAL_REVIEW`'a düşen bir
işlem bankada onaylı kaldıysa, burada ortaya çıkar.

### Gerçek ortamda denenen senaryo

| Kayıt | Kurulum | Sonuç |
|---|---|---|
| P1, 1000 TL + 300 TL iade | Normal akış | Satış ve iade `MATCHED` |
| P2, 2000 TL | Banka tarafında tutar 1999 TL'ye değiştirildi | `AMOUNT_MISMATCH` |
| P3, 500 TL | Normal akış | `MATCHED` |
| 750 TL | Sistem atlanıp doğrudan bankaya satış gönderildi | `MISSING_IN_OURS` |
| Önceki denemelerin 7 kaydı | Simülatör yeniden başladı, bellekteki işlemleri kayboldu | `MISSING_IN_BANK`: bankanın işlemi takasa göndermediği durumun aynısı |

Ledger mizanı dengedeydi (3800 = 3800), üye işyeri alacağı 3112,85 TL idi.

### Çalıştırma

Her gece 03:00'te bir önceki gün için tüm bankalarda otomatik çalışıyor. Elle de çalıştırılabilir:

```bash
curl -s -X POST localhost:8081/v1/admin/reconciliations -H 'Content-Type: application/json' \
  -d '{"bankCode":"YKB","businessDate":"2026-09-29"}'
curl -s localhost:8081/v1/admin/reconciliations/<runId>/items
```

Senaryo denemek için simülatörde:

```bash
# Banka tarafında tutarı değiştir → AMOUNT_MISMATCH
curl -s -X PUT "localhost:8090/v1/admin/banks/YKB/transactions/<paymentId>/amount?amount=199900"
# Sistemi atlayıp doğrudan bankaya satış → MISSING_IN_OURS
curl -s -X POST localhost:8090/banks/YKB/v1/authorize -H 'Content-Type: application/json' \
  -d '{"orderId":"<rastgele-uuid>","pan":"5400617020092306","expiryMonth":"12","expiryYear":"28","cvv":"000","amount":75000,"currency":"TRY","installmentCount":1}'
```
