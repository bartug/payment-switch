/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.messaging.consumer;

import com.bartugsevindik.paymentswitch.messaging.MessageHeaders;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.jetbrains.annotations.NotNull;

/**
 * <h1>EventReader</h1>
 * <p>Kafka kaydından {@code event-id} header'ını ve JSON payload'ı okur. Okunamayan mesajlar
 * {@link InvalidEventException} ile retry edilmeden DLT'ye gider.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-4
 */
@RequiredArgsConstructor
public class EventReader {

    private final ObjectMapper objectMapper;

    public <T> IncomingEvent<T> read(@NotNull ConsumerRecord<String, String> record, @NotNull Class<T> type) {
        String eventId = MessageHeaders.read(record.headers(), MessageHeaders.EVENT_ID);
        if (eventId == null || eventId.isBlank()) {
            throw new InvalidEventException("Missing event-id header. topic=" + record.topic() + ", offset=" + record.offset());
        }
        try {
            return new IncomingEvent<>(eventId, objectMapper.readValue(record.value(), type));
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new InvalidEventException("Event could not be parsed. eventId=" + eventId + ", type=" + type.getSimpleName(), e);
        }
    }
}
