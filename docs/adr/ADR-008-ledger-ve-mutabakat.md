# ADR-008: Çift taraflı ledger ve iki yönlü mutabakat

- **Durum:** Kabul edildi
- **Tarih:** 29.09.2026

## Bağlam

Önceki fazlar, para kaybını ve çift çekimi **önlemeye** yönelikti: idempotency, outbox, inbox, inquiry, reversal. Ama
bütün önlemlere rağmen fark oluşabilir. Bir bankanın işlemi takasa göndermemesi, reversal'ın başarısız olması ya da bir
kod hatası buna örnek. İki sorunun cevabı gerekiyordu:

1. Kimin kime ne kadar borçlu olduğunu nasıl güvenilir biçimde biliriz?
2. Bizim bildiğimiz ile bankanın bildiği arasındaki farkları nasıl yakalarız?

## Kararlar

### 1. Ledger ödeme servisi içinde, aynı transaction'da

**Alternatif: Ayrı bir ledger servisi, event'lerle beslenen.** Ölçeklenebilir ve sorumlulukları ayırıyor. Ama ledger
event'i geç ya da hiç işlerse "ödeme onaylı ama muhasebe kaydı yok" ara durumu oluşur ve bir consumer bug'ı parayı
yanlış hesaba yazabilir. Şu an tek bir ödeme servisi olduğu için ledger'ı aynı transaction'da yazmak en güçlü garantiyi
veriyor: ödeme ile muhasebe kaydı ya birlikte var ya birlikte yok. Hacim büyüdüğünde ledger ayrılabilir. O zaman
outbox + inbox ile beslenmeli ve periyodik iç mutabakat (ödemeler ↔ ledger) eklenmeli.

### 2. Denge kuralı veritabanında

Borç = alacak kuralı sadece uygulama kodunda olsaydı, bir hata, elle çalıştırılan bir SQL ya da ileride yazılacak
başka bir servis kuralı delebilirdi. `DEFERRABLE INITIALLY DEFERRED` constraint trigger commit anında kontrol ediyor;
satırlar tek tek eklenirken ara durum dengesiz olabiliyor, ama commit edilen hiçbir kayıt dengesiz olamıyor.

### 3. Kayıtlar değişmez (append-only)

İptal, satış kaydının ters kaydı (storno) olarak yazılıyor. Kayıt silinseydi ya da güncellenseydi "bu para dün neredeydi"
sorusunun cevabı kaybolurdu. Denetim (audit) ve olay incelemesi için geçmişin tamamı gerekiyor.

### 4. Mutabakat iki yönlü

Sadece "bizim kayıtlarımız bankada var mı" diye bakılsaydı, en tehlikeli fark kaçardı: bankada olup bizde başarısız
görünen işlem (`STATUS_MISMATCH`). Bu, kart sahibinden para çekildiği ama üye işyerine "başarısız" dendiği, kart
sahibinin de tekrar ödediği durum. Bu yüzden bankanın her satırı da bizim tarafta aranıyor.

### 5. Tarih toleransı

Gün sonu saatine yakın işlemler bankada ertesi güne yazılabiliyor. Eşleşmeyen kayıt için ±1 günlük dosyalara da
bakılıyor ve bulunursa `MATCHED_DIFFERENT_DAY` sonucu veriliyor. Bu bir hata değil, bilgi amaçlı.

## Sonuçlar

- (+) Mizan her zaman dengede. Dengesiz kayıt commit edilemiyor.
- (+) Önceki fazlarda `MANUAL_REVIEW`'a düşen işlemler ve banka tarafındaki farklar ertesi sabah mutabakatta görünüyor.
- (-) Mutabakat sadece farkı raporluyor, otomatik düzeltme yapmıyor. Bir sonraki adım olarak `STATUS_MISMATCH` için otomatik iade, `MISSING_IN_BANK` için payout blokajı eklenebilir.
- (-) Dosya formatı tek. Gerçekte her bankanın formatı farklı (sabit uzunluklu, XML, CSV); banka bazında parser gerekir.
- (-) Ledger TRY varsayıyor. Çoklu para biriminde mizan para birimi bazında tutulmalı.
- (-) Bankanın komisyon kesintisi (`BANK_FEE_EXPENSE`) henüz yok. Settlement dosyasındaki net tutar ile ayrıca mutabakat gerekir.
