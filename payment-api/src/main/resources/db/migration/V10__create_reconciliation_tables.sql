CREATE TABLE reconciliation_run
(
    id                BIGSERIAL PRIMARY KEY,
    run_id            VARCHAR(36) NOT NULL,
    bank_code         VARCHAR(16) NOT NULL,
    business_date     DATE        NOT NULL,
    matched           INTEGER     NOT NULL DEFAULT 0,
    matched_other_day INTEGER     NOT NULL DEFAULT 0,
    missing_in_bank   INTEGER     NOT NULL DEFAULT 0,
    missing_in_ours   INTEGER     NOT NULL DEFAULT 0,
    amount_mismatch   INTEGER     NOT NULL DEFAULT 0,
    status_mismatch   INTEGER     NOT NULL DEFAULT 0,
    created_date      TIMESTAMP   NOT NULL,
    updated_date      TIMESTAMP,
    version           BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_reconciliation_run_id UNIQUE (run_id)
);

CREATE INDEX idx_reconciliation_run_bank_date ON reconciliation_run (bank_code, business_date DESC);

CREATE TABLE reconciliation_item
(
    id           BIGSERIAL PRIMARY KEY,
    run_id       VARCHAR(36)  NOT NULL REFERENCES reconciliation_run (run_id),
    record_type  VARCHAR(8)   NOT NULL,
    order_id     VARCHAR(36)  NOT NULL,
    operation_id VARCHAR(36),
    result       VARCHAR(24)  NOT NULL,
    our_amount   BIGINT,
    bank_amount  BIGINT,
    our_status   VARCHAR(20),
    detail       VARCHAR(255),
    created_date TIMESTAMP    NOT NULL,
    updated_date TIMESTAMP,
    version      BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_reconciliation_item_run ON reconciliation_item (run_id, result);
