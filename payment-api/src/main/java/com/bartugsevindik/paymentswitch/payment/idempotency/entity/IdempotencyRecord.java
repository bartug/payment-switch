/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.idempotency.entity;

import com.bartugsevindik.paymentswitch.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * <h1>IdempotencyRecord</h1>
 * <p>Bir üye işyerinin gönderdiği Idempotency-Key ile oluşan kaynak (ödeme) arasındaki eşleşme.</p>
 * <p>Kaynakla <b>aynı transaction</b> içinde yazılır. {@code (merchant_id, idempotency_key)} unique constraint'i
 * Redis kilidi kaçırsa bile aynı key ile ikinci bir ödeme oluşmasını engelleyen asıl garantidir.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-2
 */
@Entity
@Table(name = "idempotency_record")
@Data
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = false)
public class IdempotencyRecord extends BaseEntity {

    /**
     * Key üye işyeri bazında tekildir. İki farklı üye işyeri aynı key'i gönderebilir.
     */
    @Column(name = "merchant_id", nullable = false, length = 32)
    private String merchantId;

    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String idempotencyKey;

    /**
     * Aynı key ile farklı body gelip gelmediğini anlamak için isteğin HMAC-SHA256 değeri.
     */
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "resource_type", nullable = false, length = 32)
    private String resourceType;

    @Column(name = "resource_id", nullable = false, length = 36)
    private String resourceId;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
}
