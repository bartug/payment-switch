/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.exception;

import com.bartugsevindik.paymentswitch.common.exception.ConflictException;
import com.bartugsevindik.paymentswitch.payment.enums.PaymentStatus;

public class InvalidPaymentStateException extends ConflictException {

    public InvalidPaymentStateException(String paymentId, PaymentStatus from, PaymentStatus to) {
        super(String.format("Ödeme %s durumundan %s durumuna geçemez. PaymentId: %s", from, to, paymentId));
    }
}
