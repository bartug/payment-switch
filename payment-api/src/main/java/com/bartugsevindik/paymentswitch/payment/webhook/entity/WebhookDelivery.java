/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.webhook.entity;

import com.bartugsevindik.paymentswitch.common.entity.BaseEntity;
import com.bartugsevindik.paymentswitch.payment.webhook.enums.WebhookDeliveryStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * <h1>WebhookDelivery</h1>
 * <p>Üye işyerine gönderilecek bildirim. Ödeme durumu değişikliğiyle aynı transaction'da yazılır; bildirim
 * kaybolmaz, durum değişmeden bildirim gitmez.</p>
 * <p>Payload yazıldığı andaki durumu taşır. Tekrar denemelerde aynı içerik ve aynı {@code deliveryId} gider;
 * üye işyeri tekrarları bu ID ile ayıklar.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 29.09.2026 - PS-6
 */
@Entity
@Table(name = "webhook_delivery")
@Data
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = false)
public class WebhookDelivery extends BaseEntity {

    @Column(name = "delivery_id", nullable = false, unique = true, length = 36, updatable = false)
    private String deliveryId;

    @Column(name = "merchant_id", nullable = false, length = 32)
    private String merchantId;

    @Column(name = "payment_id", nullable = false, length = 36)
    private String paymentId;

    @Column(name = "event_type", nullable = false, length = 48)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private WebhookDeliveryStatus status;

    @Column(name = "attempts", nullable = false)
    private Integer attempts;

    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    @Column(name = "last_status_code")
    private Integer lastStatusCode;

    @Column(name = "last_error", length = 512)
    private String lastError;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;
}
