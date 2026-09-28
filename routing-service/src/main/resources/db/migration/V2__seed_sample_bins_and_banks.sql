-- ÖRNEK VERİ. Gerçek BIN listesi değildir; test kartlarıyla routing senaryolarını denemek için hazırlanmıştır.
-- Test kartları ve beklenen sonuçlar: docs/04-routing.md

INSERT INTO acquirer_bank (bank_code, active, on_us_rate_bps, off_us_rate_bps, created_date)
VALUES ('QNB', TRUE, 170, 199, now()),
       ('YKB', TRUE, 180, 220, now()),
       ('GARANTI', TRUE, 175, 215, now()),
       ('ISBANK', TRUE, 185, 225, now()),
       ('AKBANK', TRUE, 190, 230, now());

INSERT INTO bin_range (bin_prefix, issuer_bank, card_program, card_scheme, card_type, commercial, created_date)
VALUES ('540061', 'YKB', 'WORLD', 'MASTERCARD', 'CREDIT', FALSE, now()),
       ('411111', 'GARANTI', 'BONUS', 'VISA', 'CREDIT', FALSE, now()),
       ('424242', 'ISBANK', 'MAXIMUM', 'VISA', 'CREDIT', FALSE, now()),
       ('555555', 'QNB', 'CARDFINANS', 'MASTERCARD', 'CREDIT', FALSE, now()),
       ('520082', 'AKBANK', 'AXESS', 'MASTERCARD', 'CREDIT', FALSE, now()),
       -- Aynı 6 hanenin altında farklı bankaya ait 8 haneli BIN: en uzun eşleşme kazanır
       ('400005', 'GARANTI', 'BONUS', 'VISA', 'CREDIT', FALSE, now()),
       ('40000566', 'YKB', NULL, 'VISA', 'DEBIT', FALSE, now()),
       ('979200', 'ISBANK', NULL, 'TROY', 'DEBIT', FALSE, now()),
       ('222300', 'QNB', NULL, 'MASTERCARD', 'PREPAID', FALSE, now());
