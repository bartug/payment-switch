/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.bank.entity;

import com.bartugsevindik.paymentswitch.bank.enums.BankTransactionStatus;
import com.bartugsevindik.paymentswitch.common.entity.BaseEntity;
import com.bartugsevindik.paymentswitch.common.enums.BankCode;
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
 * <h1>BankTransaction</h1>
 * <p>Bankaya gönderilen (ya da gönderilmeye çalışılan) işlem. Cevapsız kalan işlemlerin inquiry ve reversal
 * adımları bu kayıt üzerinden yürür.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-5
 */
@Entity
@Table(name = "bank_transaction")
@Data
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = false)
public class BankTransaction extends BaseEntity {

    @Column(name = "payment_id", nullable = false, unique = true, length = 36, updatable = false)
    private String paymentId;

    /**
     * Bankaya giden sipariş numarası. Banka aynı orderId'yi ikinci kez işlemez; inquiry ve reversal bununla yapılır.
     */
    @Column(name = "order_id", nullable = false, length = 36, updatable = false)
    private String orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "bank_code", nullable = false, length = 16)
    private BankCode bankCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private BankTransactionStatus status;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "installment_count", nullable = false)
    private Integer installmentCount;

    @Column(name = "response_code", length = 2)
    private String responseCode;

    @Column(name = "auth_code", length = 6)
    private String authCode;

    @Column(name = "rrn", length = 12)
    private String rrn;

    @Column(name = "message")
    private String message;

    @Column(name = "attempts", nullable = false)
    private Integer attempts;

    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    @Column(name = "last_error", length = 512)
    private String lastError;

    public void recordError(String error) {
        this.attempts = attempts + 1;
        this.lastError = error == null ? null : error.substring(0, Math.min(error.length(), 512));
    }
}
