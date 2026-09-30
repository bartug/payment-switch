-- Event yazıldığı andaki trace context (W3C traceparent). Relay başka bir thread'de, sonradan gönderdiği için
-- trace bilgisi kaybolmasın diye satırla birlikte saklanır.
ALTER TABLE outbox_event ADD COLUMN trace_parent VARCHAR(55);
