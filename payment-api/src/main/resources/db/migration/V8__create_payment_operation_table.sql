-- Onaylanan tutarın ne kadarının iade edildiği. Kısmi iadede ödeme PARTIALLY_REFUNDED olur.
ALTER TABLE payment ADD COLUMN refunded_amount BIGINT NOT NULL DEFAULT 0;
ALTER TABLE payment ADD CONSTRAINT ck_payment_refunded_amount CHECK (refunded_amount >= 0 AND refunded_amount <= amount);

-- Onaylı ödeme üzerinde yapılan iptal ve iadeler
CREATE TABLE payment_operation
(
    id             BIGSERIAL PRIMARY KEY,
    operation_id   VARCHAR(36)  NOT NULL,
    payment_id     VARCHAR(36)  NOT NULL,
    merchant_id    VARCHAR(32)  NOT NULL,
    type           VARCHAR(16)  NOT NULL,
    amount         BIGINT       NOT NULL CHECK (amount > 0),
    currency       VARCHAR(3)   NOT NULL,
    status         VARCHAR(16)  NOT NULL,
    response_code  VARCHAR(2),
    failure_reason VARCHAR(255),
    created_date   TIMESTAMP    NOT NULL,
    updated_date   TIMESTAMP,
    version        BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_payment_operation_id UNIQUE (operation_id)
);

CREATE INDEX idx_payment_operation_payment ON payment_operation (payment_id);
