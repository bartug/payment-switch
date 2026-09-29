-- İptal ve iade işlemleri. Satıştan farklı olarak cevapsız kalan istek aynı operation_id ile tekrar denenir;
-- banka operation_id'yi tanıdığı için ikinci iade oluşmaz.
CREATE TABLE bank_operation
(
    id              BIGSERIAL PRIMARY KEY,
    operation_id    VARCHAR(36)  NOT NULL,
    payment_id      VARCHAR(36)  NOT NULL,
    order_id        VARCHAR(36)  NOT NULL,
    bank_code       VARCHAR(16)  NOT NULL,
    type            VARCHAR(16)  NOT NULL,
    amount          BIGINT       NOT NULL,
    currency        VARCHAR(3)   NOT NULL,
    status          VARCHAR(16)  NOT NULL,
    response_code   VARCHAR(2),
    message         VARCHAR(255),
    attempts        INTEGER      NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP,
    last_error      VARCHAR(512),
    created_date    TIMESTAMP    NOT NULL,
    updated_date    TIMESTAMP,
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_bank_operation_id UNIQUE (operation_id)
);

CREATE INDEX idx_bank_operation_pending ON bank_operation (next_attempt_at) WHERE status = 'PENDING';
