/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.bank.entity;

import com.bartugsevindik.paymentswitch.bank.enums.BankOperationStatus;
import com.bartugsevindik.paymentswitch.common.entity.BaseEntity;
import com.bartugsevindik.paymentswitch.common.enums.BankCode;
import com.bartugsevindik.paymentswitch.common.enums.BankOperationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * <h1>BankOperation</h1>
 * <p>Bankaya gönderilen iptal ya da iade işlemi.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 29.09.2026 - PS-6
 */
@Entity
@Table(name = "bank_operation")
@Data
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = false)
public class BankOperation extends BaseEntity {

    @Column(name = "operation_id", nullable = false, unique = true, length = 36, updatable = false)
    private String operationId;

    @Column(name = "payment_id", nullable = false, length = 36)
    private String paymentId;

    @Column(name = "order_id", nullable = false, length = 36)
    private String orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "bank_code", nullable = false, length = 16)
    private BankCode bankCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16)
    private BankOperationType type;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private BankOperationStatus status;

    @Column(name = "response_code", length = 2)
    private String responseCode;

    @Column(name = "message")
    private String message;

    @Column(name = "attempts", nullable = false)
    private Integer attempts;

    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    @Column(name = "last_error", length = 512)
    private String lastError;
}
