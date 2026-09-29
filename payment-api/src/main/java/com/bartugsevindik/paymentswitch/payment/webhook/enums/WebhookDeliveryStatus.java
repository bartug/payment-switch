/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.webhook.enums;

/**
 * <h1>WebhookDeliveryStatus</h1>
 * <p>{@code FAILED}: tüm denemeler bitti. Operasyon tekrar gönderebilir, üye işyeri de GET ile sonucu sorgulayabilir.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 29.09.2026 - PS-6
 */
public enum WebhookDeliveryStatus {
    PENDING,
    DELIVERED,
    FAILED
}
