-- Inbox: consumer'ın işlediği event'ler. Kafka at-least-once teslim ettiği için aynı mesaj tekrar gelebilir.
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
