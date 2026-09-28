/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.common.event;

import com.bartugsevindik.paymentswitch.common.enums.TerminalType;

import java.time.Instant;

/**
 * <h1>PaymentRequestedEvent</h1>
 * <p>payment-api tarafından {@code payment.requested} topic'ine basılır, routing-service tarafından tüketilir.</p>
 * <p>Kart numarasının tamamı event'e konmaz. Routing için BIN yeterlidir; bankaya gidecek kart verisi
 * {@code cardToken} ile card vault'tan alınır.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-1
 */
public record PaymentRequestedEvent(
        String paymentId,
        String merchantId,
        String terminalId,
        TerminalType terminalType,
        String cardBin,
        String cardLast4,
        String cardToken,
        long amount,
        String currency,
        int installmentCount,
        Instant occurredAt
) {
    public static final String TOPIC = "payment.requested";
}
