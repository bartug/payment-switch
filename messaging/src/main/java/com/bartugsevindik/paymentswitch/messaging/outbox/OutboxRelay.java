/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.messaging.outbox;

import com.bartugsevindik.paymentswitch.messaging.MessageHeaders;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * <h1>OutboxRelay</h1>
 * <p>Outbox tablosundaki gönderilmemiş event'leri Kafka'ya basar.</p>
 * <p>Akış (tek transaction): {@code FOR UPDATE SKIP LOCKED} ile batch'i kilitle → hepsini Kafka'ya gönder →
 * onay gelenleri {@code published} işaretle → commit. Birden fazla pod aynı anda çalışabilir, aynı satırı iki pod almaz.</p>
 * <p><b>At-least-once:</b> Kafka onay verdikten sonra commit'ten önce uygulama ölürse satır gönderilmemiş kalır ve
 * tekrar gönderilir. Consumer'lar {@code event-id} header'ı ile tekrar eden mesajları ayıklamak zorundadır ({@code InboxService}).</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-3
 */
@Slf4j
@RequiredArgsConstructor
public class OutboxRelay {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final TransactionTemplate transactionTemplate;
    private final OutboxProperties properties;
    /**
     * Tracing kapalıysa null olabilir.
     */
    private final Tracer tracer;

    @Scheduled(fixedDelayString = "${application.outbox.poll-interval:200ms}")
    public void publishPending() {
        int published;
        // Birikme varsa (örn. Kafka yeni ayağa kalktı) bir sonraki tick'i beklemeden boşalt
        do {
            published = transactionTemplate.execute(status -> publishBatch());
        } while (published == properties.getBatchSize());
    }

    /**
     * Batch'teki tüm mesajlar önce gönderilir, sonra onaylar beklenir. Tek tek gönderip beklemek
     * batch süresini mesaj sayısı kadar uzatırdı.
     *
     * @return Başarıyla gönderilen event sayısı
     */
    private int publishBatch() {
        List<OutboxEvent> batch = outboxEventRepository.lockNextBatch(properties.getBatchSize());
        if (batch.isEmpty()) {
            return 0;
        }

        List<CompletableFuture<SendResult<String, String>>> futures = new ArrayList<>(batch.size());
        for (OutboxEvent event : batch) {
            CompletableFuture<SendResult<String, String>> future = send(event);
            futures.add(future);
            // Broker'a ulaşılamıyorsa her send() max.block.ms kadar bekler; kalanları denemeden batch bırakılır
            if (future.isCompletedExceptionally()) {
                break;
            }
        }

        int published = 0;
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < futures.size(); i++) {
            OutboxEvent event = batch.get(i);
            try {
                futures.get(i).get(properties.getSendTimeout().toMillis(), TimeUnit.MILLISECONDS);
                event.markPublished(now);
                published++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                event.markFailed("interrupted");
                break;
            } catch (ExecutionException | TimeoutException e) {
                Throwable cause = e instanceof ExecutionException && e.getCause() != null ? e.getCause() : e;
                event.markFailed(cause.getClass().getSimpleName() + ": " + cause.getMessage());
                log.warn("Outbox event could not be published, will retry. eventId={}, topic={}, attempts={}, cause={}",
                        event.getEventId(), event.getTopic(), event.getAttempts(), cause.getMessage());
            }
        }

        if (published > 0) {
            log.debug("Outbox events published. count={}", published);
        }
        // Batch'te hata varsa kalanlar bir sonraki tick'te tekrar denenir; döngü burada kırılır
        return published == batch.size() ? published : 0;
    }

    private CompletableFuture<SendResult<String, String>> send(OutboxEvent event) {
        ProducerRecord<String, String> record = new ProducerRecord<>(event.getTopic(), event.getMessageKey(), event.getPayload());
        record.headers()
                .add(MessageHeaders.EVENT_ID, event.getEventId().getBytes(StandardCharsets.UTF_8))
                .add(MessageHeaders.EVENT_TYPE, event.getEventType().getBytes(StandardCharsets.UTF_8));
        // Event yazıldığı andaki trace'e bağlı bir span açılır; KafkaTemplate'in producer span'ı ve traceparent
        // header'ı bu span'ın altında oluşur, consumer tarafında aynı trace devam eder
        Span span = TraceParent.restore(tracer, event.getTraceParent())
                .map(parent -> tracer.spanBuilder().setParent(parent).name("outbox publish " + event.getTopic())
                        .tag("outbox.event_type", event.getEventType())
                        .tag("outbox.attempts", String.valueOf(event.getAttempts()))
                        .start())
                .orElse(null);
        try (Tracer.SpanInScope ignored = span == null ? null : tracer.withSpan(span)) {
            return kafkaTemplate.send(record);
        } catch (RuntimeException e) {
            // Metadata alınamazsa send() future dönmeden exception fırlatabilir (max.block.ms)
            return CompletableFuture.failedFuture(e);
        } finally {
            if (span != null) {
                span.end();
            }
        }
    }
}
