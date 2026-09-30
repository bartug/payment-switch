/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.messaging.outbox;

import com.bartugsevindik.paymentswitch.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * <h1>OutboxEvent</h1>
 * <p>Kafka'ya gönderilecek event. İş verisiyle (payment) <b>aynı transaction</b> içinde yazılır,
 * {@code OutboxRelay} tarafından okunup Kafka'ya basılır.</p>
 * <p>{@code publishedAt} boş olan kayıtlar henüz gönderilmemiştir.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-3
 */
@Entity
@Table(name = "outbox_event")
@Data
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = false)
public class OutboxEvent extends BaseEntity {

    @Column(name = "event_id", nullable = false, unique = true, length = 36, updatable = false)
    private String eventId;

    @Column(name = "aggregate_type", nullable = false, length = 32)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 36)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "topic", nullable = false, length = 128)
    private String topic;

    /**
     * Kafka mesaj key'i. Aynı key aynı partition'a düşer; aynı ödemenin event'leri sırayla işlenir.
     */
    @Column(name = "message_key", nullable = false, length = 64)
    private String messageKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false)
    private String payload;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "attempts", nullable = false)
    private Integer attempts;

    @Column(name = "last_error", length = 512)
    private String lastError;

    /**
     * Event yazıldığı andaki trace context (W3C traceparent). Relay gönderirken geri yükler.
     */
    @Column(name = "trace_parent", length = 55)
    private String traceParent;

    public void markPublished(LocalDateTime now) {
        this.publishedAt = now;
        this.attempts = attempts + 1;
        this.lastError = null;
    }

    public void markFailed(String error) {
        this.attempts = attempts + 1;
        this.lastError = error == null ? null : error.substring(0, Math.min(error.length(), 512));
    }
}
