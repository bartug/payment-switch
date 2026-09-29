CREATE TABLE merchant
(
    id                        BIGSERIAL PRIMARY KEY,
    merchant_id               VARCHAR(32)  NOT NULL,
    name                      VARCHAR(128) NOT NULL,
    webhook_url               VARCHAR(512),
    -- Webhook imzalamak için; üye işyeri de doğrulamak için aynı secret'ı kullanır, bu yüzden hash değil şifreli
    webhook_secret_ciphertext TEXT,
    created_date              TIMESTAMP    NOT NULL,
    updated_date              TIMESTAMP,
    version                   BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_merchant_merchant_id UNIQUE (merchant_id)
);

-- Ödeme durum değişikliğiyle aynı transaction'da yazılır (outbox mantığı), dispatcher tarafından gönderilir
CREATE TABLE webhook_delivery
(
    id               BIGSERIAL PRIMARY KEY,
    delivery_id      VARCHAR(36)  NOT NULL,
    merchant_id      VARCHAR(32)  NOT NULL,
    payment_id       VARCHAR(36)  NOT NULL,
    event_type       VARCHAR(48)  NOT NULL,
    payload          JSONB        NOT NULL,
    status           VARCHAR(16)  NOT NULL,
    attempts         INTEGER      NOT NULL DEFAULT 0,
    next_attempt_at  TIMESTAMP,
    last_status_code INTEGER,
    last_error       VARCHAR(512),
    delivered_at     TIMESTAMP,
    created_date     TIMESTAMP    NOT NULL,
    updated_date     TIMESTAMP,
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_webhook_delivery_id UNIQUE (delivery_id)
);

CREATE INDEX idx_webhook_delivery_pending ON webhook_delivery (next_attempt_at) WHERE status = 'PENDING';
CREATE INDEX idx_webhook_delivery_payment ON webhook_delivery (payment_id);
