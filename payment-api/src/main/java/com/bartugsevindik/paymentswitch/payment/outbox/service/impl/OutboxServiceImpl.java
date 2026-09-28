/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.outbox.service.impl;

import com.bartugsevindik.paymentswitch.payment.outbox.config.OutboxProperties;
import com.bartugsevindik.paymentswitch.payment.outbox.entity.OutboxEvent;
import com.bartugsevindik.paymentswitch.payment.outbox.repository.OutboxEventRepository;
import com.bartugsevindik.paymentswitch.payment.outbox.service.OutboxService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxServiceImpl implements OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * <h1>Event Kaydetme</h1>
     * <p>Event'i outbox tablosuna yazar. İş verisiyle <b>aynı transaction</b> içinde çağrılmak zorundadır;
     * transaction yoksa exception fırlatılır. Kafka'ya gönderim relay tarafından asenkron yapılır.</p>
     *
     * @param aggregateType Event'in ait olduğu kaynak tipi (örn. PAYMENT)
     * @param aggregateId   Kaynak ID. Kafka mesaj key'i olarak da kullanılır.
     * @param topic         Hedef topic
     * @param event         Event nesnesi, JSON'a çevrilir
     * @return Oluşan event ID
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-3
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public String enqueue(@NotNull String aggregateType, @NotNull String aggregateId, @NotNull String topic, @NotNull Object event) {
        String eventId = UUID.randomUUID().toString();
        outboxEventRepository.save(OutboxEvent.builder()
                .eventId(eventId)
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventType(event.getClass().getSimpleName())
                .topic(topic)
                .messageKey(aggregateId)
                .payload(toJson(event))
                .attempts(0)
                .build());
        return eventId;
    }

    /**
     * <h1>Gönderilmiş Event'leri Silme</h1>
     * <p>Saklama süresi dolan gönderilmiş event'leri siler.</p>
     *
     * @return Silinen kayıt sayısı
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-3
     */
    @Override
    @Transactional
    public int deletePublishedEvents() {
        return outboxEventRepository.deletePublishedBefore(
                LocalDateTime.now().minus(properties.getRetention()), properties.getCleanupBatchSize());
    }

    private String toJson(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Outbox event could not be serialized: " + event.getClass().getSimpleName(), e);
        }
    }
}
