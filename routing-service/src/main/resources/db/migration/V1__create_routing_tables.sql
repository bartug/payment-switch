CREATE TABLE bin_range
(
    id           BIGSERIAL PRIMARY KEY,
    -- 6 veya 8 hane. Çözümleme en uzun prefix eşleşmesi ile yapılır.
    bin_prefix   VARCHAR(8)  NOT NULL,
    issuer_bank  VARCHAR(16) NOT NULL,
    card_program VARCHAR(16),
    card_scheme  VARCHAR(16) NOT NULL,
    card_type    VARCHAR(16) NOT NULL,
    commercial   BOOLEAN     NOT NULL DEFAULT FALSE,
    created_date TIMESTAMP   NOT NULL,
    updated_date TIMESTAMP,
    version      BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_bin_range_prefix UNIQUE (bin_prefix),
    CONSTRAINT ck_bin_range_length CHECK (length(bin_prefix) IN (6, 8))
);

-- İşlem gönderebildiğimiz (POS'u olan) bankalar ve komisyon oranları. Oranlar baz puan: 180 = %1,80
CREATE TABLE acquirer_bank
(
    id              BIGSERIAL PRIMARY KEY,
    bank_code       VARCHAR(16) NOT NULL,
    active          BOOLEAN     NOT NULL DEFAULT TRUE,
    on_us_rate_bps  INTEGER     NOT NULL,
    off_us_rate_bps INTEGER     NOT NULL,
    created_date    TIMESTAMP   NOT NULL,
    updated_date    TIMESTAMP,
    version         BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_acquirer_bank_code UNIQUE (bank_code),
    CONSTRAINT ck_acquirer_bank_rates CHECK (on_us_rate_bps > 0 AND off_us_rate_bps > 0)
);

CREATE TABLE routing_decision
(
    id                BIGSERIAL PRIMARY KEY,
    -- Aynı ödeme iki kez yönlendirilemez; inbox'ı atlatan bir tekrar olsa bile ikinci kayıt yazılamaz
    payment_id        VARCHAR(36) NOT NULL,
    outcome           VARCHAR(16) NOT NULL,
    bank_code         VARCHAR(16),
    on_us             BOOLEAN     NOT NULL DEFAULT FALSE,
    reason            VARCHAR(48) NOT NULL,
    card_bin          VARCHAR(8)  NOT NULL,
    installment_count INTEGER     NOT NULL,
    created_date      TIMESTAMP   NOT NULL,
    updated_date      TIMESTAMP,
    version           BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_routing_decision_payment UNIQUE (payment_id)
);

CREATE INDEX idx_routing_decision_bank_created ON routing_decision (bank_code, created_date DESC);

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
