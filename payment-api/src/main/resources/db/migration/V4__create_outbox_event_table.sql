CREATE TABLE outbox_event
(
    id             BIGSERIAL PRIMARY KEY,
    -- Consumer tarafında tekrar eden mesajları ayıklamak için (inbox). Kafka header'ında da gider.
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

-- Relay sadece gönderilmemiş kayıtları tarar; partial index gönderilmişler büyüdükçe küçük kalır
CREATE INDEX idx_outbox_unpublished ON outbox_event (id) WHERE published_at IS NULL;
CREATE INDEX idx_outbox_published_at ON outbox_event (published_at) WHERE published_at IS NOT NULL;
