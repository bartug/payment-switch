/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.webhook.enums;

import com.bartugsevindik.paymentswitch.payment.enums.PaymentStatus;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Optional;

/**
 * <h1>WebhookEventType</h1>
 * <p>Üye işyerine bildirilen olaylar. Ara durumlar ({@code PENDING}, {@code ROUTED}, {@code UNKNOWN}) bildirilmez;
 * üye işyeri sadece sonuçla ilgilenir.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 29.09.2026 - PS-6
 */
@Getter
@RequiredArgsConstructor
public enum WebhookEventType {
    PAYMENT_APPROVED("payment.approved", PaymentStatus.APPROVED),
    PAYMENT_DECLINED("payment.declined", PaymentStatus.DECLINED),
    PAYMENT_FAILED("payment.failed", PaymentStatus.FAILED),
    PAYMENT_REVERSED("payment.reversed", PaymentStatus.REVERSED);

    private final String value;
    private final PaymentStatus paymentStatus;

    public static Optional<WebhookEventType> of(PaymentStatus status) {
        return Arrays.stream(values()).filter(type -> type.paymentStatus == status).findFirst();
    }
}
