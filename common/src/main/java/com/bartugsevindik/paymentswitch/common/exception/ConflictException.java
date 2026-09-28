/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@Getter
@ResponseStatus(HttpStatus.CONFLICT)
public class ConflictException extends RuntimeException {
    private final String message;

    /**
     * Doluysa cevaba {@code Retry-After} header'ı eklenir. Client'ın ne kadar bekleyip tekrar deneyeceğini söyler.
     */
    private final Integer retryAfterSeconds;

    public ConflictException(String message) {
        this(message, null);
    }

    public ConflictException(String message, Integer retryAfterSeconds) {
        super(message);
        this.message = message;
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
