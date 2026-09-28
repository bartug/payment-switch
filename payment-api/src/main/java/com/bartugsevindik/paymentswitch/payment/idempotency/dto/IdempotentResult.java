/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.idempotency.dto;

/**
 * <h1>IdempotentResult</h1>
 * <p>{@code replayed = true} ise istek daha önce işlenmiştir ve yeni bir kaynak oluşturulmamıştır.
 * Controller bu durumda {@code Idempotent-Replayed: true} header'ını ekler.</p>
 *
 * @param body     Cevap
 * @param replayed Tekrar eden istek mi
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-2
 */
public record IdempotentResult<T>(T body, boolean replayed) {

    public static <T> IdempotentResult<T> created(T body) {
        return new IdempotentResult<>(body, false);
    }

    public static <T> IdempotentResult<T> replayed(T body) {
        return new IdempotentResult<>(body, true);
    }
}
