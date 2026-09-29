/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.webhook.service;

import org.jetbrains.annotations.NotNull;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

/**
 * <h1>WebhookSigner</h1>
 * <p>Webhook imzası: {@code X-Webhook-Signature: t=<unix saniye>,v1=<hex(HMAC-SHA256(secret, t + "." + body))>}.</p>
 * <p>Zaman damgası imzaya dahildir. Üye işyeri eski damgalı bildirimleri reddederek yakalanan bir bildirimin
 * sonradan tekrar gönderilmesini (replay) engeller. {@code v1} ön eki algoritma değişirse eskiyle yeniyi birlikte
 * göndermeye izin verir.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 29.09.2026 - PS-6
 */
public final class WebhookSigner {

    public static final String SIGNATURE_HEADER = "X-Webhook-Signature";
    public static final String DELIVERY_ID_HEADER = "X-Webhook-Id";
    public static final String EVENT_TYPE_HEADER = "X-Webhook-Event";

    private WebhookSigner() {
    }

    public static String signatureHeader(@NotNull String secret, long timestamp, @NotNull String body) {
        return "t=" + timestamp + ",v1=" + hmacHex(secret, timestamp + "." + body);
    }

    public static String hmacHex(@NotNull String secret, @NotNull String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Webhook could not be signed", e);
        }
    }
}
