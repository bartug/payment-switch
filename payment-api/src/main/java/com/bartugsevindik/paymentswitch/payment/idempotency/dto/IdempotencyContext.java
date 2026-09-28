/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.idempotency.dto;

/**
 * <h1>IdempotencyContext</h1>
 * <p>Bir isteğin idempotency kimliği: kim gönderdi, hangi key ile, body'nin hash'i ne.</p>
 *
 * @param merchantId     Üye işyeri numarası
 * @param idempotencyKey Client'ın ürettiği key
 * @param requestHash    İsteğin HMAC-SHA256 değeri
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-2
 */
public record IdempotencyContext(String merchantId, String idempotencyKey, String requestHash) {

    public String lockKey() {
        return "idem:lock:" + merchantId + ":" + idempotencyKey;
    }
}
