-- Kart verisi bankaya gönderilene kadar burada şifreli durur, banka cevabıyla birlikte silinir.
-- Kafka'ya kart verisi değil sadece token gider.
CREATE TABLE card_vault
(
    id                   BIGSERIAL PRIMARY KEY,
    token                VARCHAR(36) NOT NULL,
    payment_id           VARCHAR(36) NOT NULL,
    card_data_ciphertext TEXT        NOT NULL,
    expires_at           TIMESTAMP   NOT NULL,
    created_date         TIMESTAMP   NOT NULL,
    updated_date         TIMESTAMP,
    version              BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_card_vault_token UNIQUE (token),
    CONSTRAINT uk_card_vault_payment UNIQUE (payment_id)
);

CREATE INDEX idx_card_vault_expires_at ON card_vault (expires_at);
