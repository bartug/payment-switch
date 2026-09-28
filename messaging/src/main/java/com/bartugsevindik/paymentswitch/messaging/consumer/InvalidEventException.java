/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.messaging.consumer;

/**
 * Tekrar denemekle düzelmeyecek mesajlar (bozuk JSON, eksik header). Retry yapılmadan doğrudan DLT'ye gönderilir.
 */
public class InvalidEventException extends RuntimeException {

    public InvalidEventException(String message) {
        super(message);
    }

    public InvalidEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
