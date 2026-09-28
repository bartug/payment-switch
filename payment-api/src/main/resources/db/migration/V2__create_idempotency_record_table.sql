CREATE TABLE idempotency_record
(
    id              BIGSERIAL PRIMARY KEY,
    merchant_id     VARCHAR(32) NOT NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    request_hash    VARCHAR(64) NOT NULL,
    resource_type   VARCHAR(32) NOT NULL,
    resource_id     VARCHAR(36) NOT NULL,
    expires_at      TIMESTAMP   NOT NULL,
    created_date    TIMESTAMP   NOT NULL,
    updated_date    TIMESTAMP,
    version         BIGINT      NOT NULL DEFAULT 0,
    -- Çift ödemeye karşı asıl garanti bu constraint. Redis kilidi sadece hızlı ön kontrol.
    CONSTRAINT uk_idempotency_merchant_key UNIQUE (merchant_id, idempotency_key)
);

CREATE INDEX idx_idempotency_expires_at ON idempotency_record (expires_at);
