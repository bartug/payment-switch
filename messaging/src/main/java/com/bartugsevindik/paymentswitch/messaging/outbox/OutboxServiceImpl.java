/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.messaging.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.UUID;

@RequiredArgsConstructor
public class OutboxServiceImpl implements OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxProperties properties;
    private final ObjectMapper objectMapper;
    /**
     * Tracing kapalıysa null olabilir.
     */
    private final Tracer tracer;
    private final OutboxRelayTrigger relayTrigger;

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
                .traceParent(TraceParent.capture(tracer))
                .attempts(0)
                .build());
        // Commit'ten önce tetiklenirse relay satırı henüz göremez; rollback olursa gönderilecek bir şey yoktur
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                relayTrigger.wakeUp();
            }
        });
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
