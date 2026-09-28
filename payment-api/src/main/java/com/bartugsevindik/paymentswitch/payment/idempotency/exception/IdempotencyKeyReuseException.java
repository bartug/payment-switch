/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.idempotency.exception;

import com.bartugsevindik.paymentswitch.common.exception.UnprocessableEntityException;

public class IdempotencyKeyReuseException extends UnprocessableEntityException {

    public IdempotencyKeyReuseException(String idempotencyKey) {
        super("Idempotency-Key daha önce farklı bir istek ile kullanılmış. Key: " + idempotencyKey);
    }
}
