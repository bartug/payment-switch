/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.common.event;

import com.bartugsevindik.paymentswitch.common.enums.BankCode;

import java.time.Instant;

/**
 * <h1>PaymentRoutingResultEvent</h1>
 * <p>routing-service'in kararı. payment-api bu event ile ödemeyi {@code ROUTED} ya da {@code FAILED} durumuna çeker.</p>
 *
 * @param routed   Bir bankaya yönlendirildi mi
 * @param bankCode Yönlendirilen banka. {@code routed = false} ise boştur.
 * @param reason   Kararın sebebi (örn. {@code ON_US_INSTALLMENT}, {@code DEBIT_CARD_INSTALLMENT})
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-4
 */
public record PaymentRoutingResultEvent(
        String paymentId,
        boolean routed,
        BankCode bankCode,
        boolean onUs,
        String reason,
        String description,
        Instant occurredAt
) {
    public static final String TOPIC = "payment.routing.results";
}
