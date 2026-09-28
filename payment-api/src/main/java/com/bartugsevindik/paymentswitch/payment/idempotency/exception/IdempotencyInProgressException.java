/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.idempotency.exception;

import com.bartugsevindik.paymentswitch.common.exception.ConflictException;

public class IdempotencyInProgressException extends ConflictException {

    public IdempotencyInProgressException(String idempotencyKey) {
        super("Aynı Idempotency-Key ile gelen istek hâlâ işleniyor. Key: " + idempotencyKey, 1);
    }
}
