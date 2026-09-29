/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.common.event;

import com.bartugsevindik.paymentswitch.common.enums.BankOperationType;
import com.bartugsevindik.paymentswitch.common.enums.OperationResultStatus;

import java.time.Instant;

/**
 * <h1>BankOperationResultEvent</h1>
 * <p>bank-adapter'ın iptal / iade sonucu.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 29.09.2026 - PS-6
 */
public record BankOperationResultEvent(
        String operationId,
        String paymentId,
        BankOperationType type,
        OperationResultStatus status,
        String responseCode,
        String message,
        Instant occurredAt
) {
    public static final String TOPIC = "payment.bank.operation-results";
}
