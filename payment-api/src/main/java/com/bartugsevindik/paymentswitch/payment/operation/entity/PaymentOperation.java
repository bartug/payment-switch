/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.operation.entity;

import com.bartugsevindik.paymentswitch.common.entity.BaseEntity;
import com.bartugsevindik.paymentswitch.common.enums.BankOperationType;
import com.bartugsevindik.paymentswitch.payment.operation.enums.PaymentOperationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * <h1>PaymentOperation</h1>
 * <p>Onaylı ödeme üzerinde yapılan iptal ya da iade.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 29.09.2026 - PS-6
 */
@Entity
@Table(name = "payment_operation")
@Data
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = false)
public class PaymentOperation extends BaseEntity {

    /**
     * Bankaya da bu ID gider; banka tekrar gelen isteği bu ID ile tanır.
     */
    @Column(name = "operation_id", nullable = false, unique = true, length = 36, updatable = false)
    private String operationId;

    @Column(name = "payment_id", nullable = false, length = 36)
    private String paymentId;

    @Column(name = "merchant_id", nullable = false, length = 32)
    private String merchantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16)
    private BankOperationType type;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private PaymentOperationStatus status;

    @Column(name = "response_code", length = 2)
    private String responseCode;

    @Column(name = "failure_reason")
    private String failureReason;
}
