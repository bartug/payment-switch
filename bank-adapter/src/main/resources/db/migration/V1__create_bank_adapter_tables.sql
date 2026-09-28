CREATE TABLE bank_transaction
(
    id                BIGSERIAL PRIMARY KEY,
    payment_id        VARCHAR(36) NOT NULL,
    -- Bankaya giden sipariş numarası. Inquiry ve reversal bununla yapılır; banka aynı orderId'yi ikinci kez işlemez.
    order_id          VARCHAR(36) NOT NULL,
    bank_code         VARCHAR(16) NOT NULL,
    status            VARCHAR(16) NOT NULL,
    amount            BIGINT      NOT NULL,
    currency          VARCHAR(3)  NOT NULL,
    installment_count INTEGER     NOT NULL,
    response_code     VARCHAR(2),
    auth_code         VARCHAR(6),
    rrn               VARCHAR(12),
    message           VARCHAR(255),
    -- Inquiry / reversal denemeleri
    attempts          INTEGER     NOT NULL DEFAULT 0,
    next_attempt_at   TIMESTAMP,
    last_error        VARCHAR(512),
    created_date      TIMESTAMP   NOT NULL,
    updated_date      TIMESTAMP,
    version           BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_bank_transaction_payment UNIQUE (payment_id),
    CONSTRAINT uk_bank_transaction_order UNIQUE (bank_code, order_id)
);

-- Recovery job'u sadece sonuçlanmamış işlemleri tarar
CREATE INDEX idx_bank_transaction_pending ON bank_transaction (next_attempt_at)
    WHERE status IN ('SENDING', 'UNKNOWN', 'REVERSING');

-- messaging modülü tabloları
CREATE TABLE outbox_event
(
    id             BIGSERIAL PRIMARY KEY,
    event_id       VARCHAR(36)  NOT NULL,
    aggregate_type VARCHAR(32)  NOT NULL,
    aggregate_id   VARCHAR(36)  NOT NULL,
    event_type     VARCHAR(64)  NOT NULL,
    topic          VARCHAR(128) NOT NULL,
    message_key    VARCHAR(64)  NOT NULL,
    payload        JSONB        NOT NULL,
    published_at   TIMESTAMP,
    attempts       INTEGER      NOT NULL DEFAULT 0,
    last_error     VARCHAR(512),
    created_date   TIMESTAMP    NOT NULL,
    updated_date   TIMESTAMP,
    version        BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_outbox_event_id UNIQUE (event_id)
);

CREATE INDEX idx_outbox_unpublished ON outbox_event (id) WHERE published_at IS NULL;
CREATE INDEX idx_outbox_published_at ON outbox_event (published_at) WHERE published_at IS NOT NULL;

CREATE TABLE processed_event
(
    id           BIGSERIAL PRIMARY KEY,
    event_id     VARCHAR(36) NOT NULL,
    consumer     VARCHAR(64) NOT NULL,
    created_date TIMESTAMP   NOT NULL,
    updated_date TIMESTAMP,
    version      BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_processed_event UNIQUE (event_id, consumer)
);
