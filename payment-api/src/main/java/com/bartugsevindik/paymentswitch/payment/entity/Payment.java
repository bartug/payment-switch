/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.entity;

import com.bartugsevindik.paymentswitch.common.entity.BaseEntity;
import com.bartugsevindik.paymentswitch.common.enums.BankCode;
import com.bartugsevindik.paymentswitch.common.enums.TerminalType;
import com.bartugsevindik.paymentswitch.common.model.Money;
import com.bartugsevindik.paymentswitch.payment.enums.PaymentStatus;
import com.bartugsevindik.paymentswitch.payment.exception.InvalidPaymentStateException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.jetbrains.annotations.NotNull;

/**
 * <h1>Payment</h1>
 * <p>POS'tan gelen tek bir ödeme işlemi. Kart numarasının tamamı ve CVV <b>saklanmaz</b> (PCI DSS),
 * sadece routing için BIN ve ekranda göstermek için son 4 hane tutulur.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-1
 */
@Entity
@Table(name = "payment")
@Data
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = false)
public class Payment extends BaseEntity {

    /**
     * Dışarıya açılan ID. DB'nin sıralı ID'si dışarı verilmez (işlem hacmi tahmin edilebilir).
     */
    @Column(name = "payment_id", nullable = false, unique = true, length = 36, updatable = false)
    private String paymentId;

    @Column(name = "merchant_id", nullable = false, length = 32)
    private String merchantId;

    @Column(name = "terminal_id", nullable = false, length = 32)
    private String terminalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "terminal_type", nullable = false, length = 16)
    private TerminalType terminalType;

    /**
     * En küçük birim cinsinden (kuruş).
     */
    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "installment_count", nullable = false)
    private Integer installmentCount;

    @Column(name = "card_bin", nullable = false, length = 8)
    private String cardBin;

    @Column(name = "card_last4", nullable = false, length = 4)
    private String cardLast4;

    @Setter(lombok.AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    private PaymentStatus paymentStatus;

    /**
     * Routing sonrası işlemin gönderildiği acquirer banka.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "bank_code", length = 16)
    private BankCode bankCode;

    @Column(name = "auth_code", length = 6)
    private String authCode;

    @Column(name = "rrn", length = 12)
    private String rrn;

    @Column(name = "response_code", length = 2)
    private String responseCode;

    @Column(name = "failure_reason")
    private String failureReason;

    /**
     * Başarıyla iade edilen toplam tutar (kuruş). Bekleyen iadeler dahil değildir.
     */
    @Column(name = "refunded_amount", nullable = false)
    @Builder.Default
    private Long refundedAmount = 0L;

    public Money getMoney() {
        return Money.ofMinor(amount, currency);
    }

    /**
     * <h1>Durum Değiştirme</h1>
     * <p>Durum sadece bu metot üzerinden değişir. {@link PaymentStatus} içinde tanımlı olmayan geçişlerde exception fırlatılır.</p>
     *
     * @param target Yeni durum
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-1
     */
    public void changeStatus(@NotNull PaymentStatus target) {
        if (paymentStatus != null && !paymentStatus.canTransitionTo(target)) {
            throw new InvalidPaymentStateException(paymentId, paymentStatus, target);
        }
        this.paymentStatus = target;
    }
}
