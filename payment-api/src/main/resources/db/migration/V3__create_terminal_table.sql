CREATE TABLE terminal
(
    id                BIGSERIAL PRIMARY KEY,
    terminal_id       VARCHAR(32) NOT NULL,
    merchant_id       VARCHAR(32) NOT NULL,
    terminal_type     VARCHAR(16) NOT NULL,
    -- AES-256-GCM ile şifreli. Format: v1:Base64(iv || ciphertext+tag)
    secret_ciphertext TEXT        NOT NULL,
    terminal_status   VARCHAR(16) NOT NULL,
    created_date      TIMESTAMP   NOT NULL,
    updated_date      TIMESTAMP,
    version           BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_terminal_terminal_id UNIQUE (terminal_id)
);

CREATE INDEX idx_terminal_merchant ON terminal (merchant_id);
CREATE INDEX idx_payment_payment_merchant ON payment (payment_id, merchant_id);
