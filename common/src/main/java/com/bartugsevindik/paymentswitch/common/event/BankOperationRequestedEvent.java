/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.common.event;

import com.bartugsevindik.paymentswitch.common.enums.BankCode;
import com.bartugsevindik.paymentswitch.common.enums.BankOperationType;

import java.time.Instant;

/**
 * <h1>BankOperationRequestedEvent</h1>
 * <p>Onaylı bir ödeme için iptal ya da iade isteği. Satış ile <b>aynı banka topic'ine, aynı key (paymentId) ile</b>
 * basılır; aynı partition'a düştüğü için satış isteğinden önce işlenemez.</p>
 *
 * @param operationId Bankada idempotency anahtarı. Aynı operationId ile tekrar gelen istek ikinci iade oluşturmaz;
 *                    bu yüzden satıştan farklı olarak retry güvenlidir.
 * @param amount      İade tutarı (kuruş). İptalde ödemenin tamamı.
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 29.09.2026 - PS-6
 */
public record BankOperationRequestedEvent(
        String operationId,
        String paymentId,
        BankCode bankCode,
        BankOperationType type,
        long amount,
        String currency,
        Instant occurredAt
) {
}
