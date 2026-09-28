/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.common.event;

import com.bartugsevindik.paymentswitch.common.enums.BankCode;
import com.bartugsevindik.paymentswitch.common.enums.TerminalType;

import java.time.Instant;

/**
 * <h1>BankAuthorizationRequestedEvent</h1>
 * <p>routing-service tarafından seçilen bankanın topic'ine ({@code bank.requests.{BANK}}) basılır,
 * o bankanın adapter'ı tarafından tüketilir.</p>
 *
 * @param onUs Kart ile POS aynı bankaya mı ait. On-us işlem BKM'ye gitmez, komisyonu düşüktür.
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-4
 */
public record BankAuthorizationRequestedEvent(
        String paymentId,
        String merchantId,
        String terminalId,
        TerminalType terminalType,
        BankCode bankCode,
        boolean onUs,
        String cardBin,
        String cardLast4,
        long amount,
        String currency,
        int installmentCount,
        String routingReason,
        Instant occurredAt
) {
}
