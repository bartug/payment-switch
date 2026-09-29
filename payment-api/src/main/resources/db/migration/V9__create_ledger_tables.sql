-- Üye işyerinden kesilen komisyon (MDR). Baz puan: 249 = %2,49
ALTER TABLE merchant ADD COLUMN commission_rate_bps INTEGER NOT NULL DEFAULT 249;

CREATE TABLE journal_entry
(
    id           BIGSERIAL PRIMARY KEY,
    entry_id     VARCHAR(36) NOT NULL,
    payment_id   VARCHAR(36) NOT NULL,
    -- İade / iptal kaydında ilgili işlem; satışta ödeme ID'si
    reference_id VARCHAR(36) NOT NULL,
    entry_type   VARCHAR(16) NOT NULL,
    currency     VARCHAR(3)  NOT NULL,
    created_date TIMESTAMP   NOT NULL,
    updated_date TIMESTAMP,
    version      BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_journal_entry_id UNIQUE (entry_id),
    -- Aynı olay iki kez muhasebeleşemez (tekrar gelen event, tekrar çalışan kod)
    CONSTRAINT uk_journal_entry_reference UNIQUE (entry_type, reference_id)
);

CREATE INDEX idx_journal_entry_payment ON journal_entry (payment_id);

CREATE TABLE journal_line
(
    id           BIGSERIAL PRIMARY KEY,
    entry_id     VARCHAR(36)  NOT NULL REFERENCES journal_entry (entry_id),
    account_code VARCHAR(64)  NOT NULL,
    account_type VARCHAR(16)  NOT NULL,
    direction    VARCHAR(6)   NOT NULL CHECK (direction IN ('DEBIT', 'CREDIT')),
    amount       BIGINT       NOT NULL CHECK (amount > 0),
    currency     VARCHAR(3)   NOT NULL,
    created_date TIMESTAMP    NOT NULL,
    updated_date TIMESTAMP,
    version      BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_journal_line_entry ON journal_line (entry_id);
CREATE INDEX idx_journal_line_account ON journal_line (account_code);

-- Çift taraflı kaydın temel kuralı veritabanında: her kaydın borç toplamı alacak toplamına eşit olmalı.
-- DEFERRABLE INITIALLY DEFERRED: satırlar tek tek eklenirken değil, commit anında kontrol edilir.
-- Uygulama kodunda bir hata olsa bile dengesiz bir kayıt commit edilemez.
CREATE FUNCTION check_journal_entry_balanced() RETURNS TRIGGER AS
$$
DECLARE
    balance BIGINT;
BEGIN
    SELECT COALESCE(SUM(CASE WHEN direction = 'DEBIT' THEN amount ELSE -amount END), 0)
    INTO balance
    FROM journal_line
    WHERE entry_id = NEW.entry_id;

    IF balance <> 0 THEN
        RAISE EXCEPTION 'Journal entry % is not balanced (debit - credit = %)', NEW.entry_id, balance;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_journal_entry_balanced
    AFTER INSERT OR UPDATE ON journal_line
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
EXECUTE FUNCTION check_journal_entry_balanced();
