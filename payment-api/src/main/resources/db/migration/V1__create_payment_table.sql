CREATE TABLE payment
(
    id                BIGSERIAL PRIMARY KEY,
    payment_id        VARCHAR(36)  NOT NULL,
    merchant_id       VARCHAR(32)  NOT NULL,
    terminal_id       VARCHAR(32)  NOT NULL,
    terminal_type     VARCHAR(16)  NOT NULL,
    amount            BIGINT       NOT NULL CHECK (amount > 0),
    currency          VARCHAR(3)   NOT NULL,
    installment_count INTEGER      NOT NULL DEFAULT 1,
    card_bin          VARCHAR(8)   NOT NULL,
    card_last4        VARCHAR(4)   NOT NULL,
    payment_status    VARCHAR(20)  NOT NULL,
    bank_code         VARCHAR(16),
    auth_code         VARCHAR(6),
    rrn               VARCHAR(12),
    response_code     VARCHAR(2),
    failure_reason    VARCHAR(255),
    created_date      TIMESTAMP    NOT NULL,
    updated_date      TIMESTAMP,
    version           BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_payment_payment_id UNIQUE (payment_id)
);

-- Üye işyeri ekranı ve gün sonu için
CREATE INDEX idx_payment_merchant_created ON payment (merchant_id, created_date DESC);
-- Timeout job'u UNKNOWN / SENT_TO_BANK kayıtları tarayacak
CREATE INDEX idx_payment_status ON payment (payment_status) WHERE payment_status IN ('SENT_TO_BANK', 'UNKNOWN');
