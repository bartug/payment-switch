/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.merchant.dto;

/**
 * Webhook gönderimi için adres ve çözülmüş secret. Sadece bellekte yaşar.
 */
public record WebhookTarget(String url, String secret) {

    @Override
    public String toString() {
        return "WebhookTarget[url=" + url + ", secret=***]";
    }
}
