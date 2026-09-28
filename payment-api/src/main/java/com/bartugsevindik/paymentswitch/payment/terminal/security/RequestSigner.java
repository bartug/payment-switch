/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.terminal.security;

import org.jetbrains.annotations.NotNull;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;

/**
 * <h1>RequestSigner</h1>
 * <p>Terminal isteklerinin imzasını hesaplar. İmzalanan metin:</p>
 * <pre>
 * METHOD \n
 * PATH(?QUERY) \n
 * TIMESTAMP \n
 * IDEMPOTENCY_KEY (yoksa boş) \n
 * hex(SHA-256(body))
 * </pre>
 * <p>Idempotency-Key imzaya dahildir. Yakalanan bir istekte key değiştirilip yeni ödeme oluşturulamaz.</p>
 * <p>Terminal SDK'sı ile birebir aynı olmalı, bu yüzden sınıf state tutmaz ve Spring'e bağımlı değildir.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-2
 */
public final class RequestSigner {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private RequestSigner() {
    }

    public static String stringToSign(@NotNull String method, @NotNull String pathWithQuery, @NotNull String timestamp,
                                      String idempotencyKey, byte @NotNull [] body) {
        return String.join("\n",
                method.toUpperCase(),
                pathWithQuery,
                timestamp,
                idempotencyKey == null ? "" : idempotencyKey,
                sha256Hex(body));
    }

    public static String sign(@NotNull String secret, @NotNull String stringToSign) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return Base64.getEncoder().encodeToString(mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Request could not be signed", e);
        }
    }

    /**
     * İmzalar sabit sürede karşılaştırılır. {@code equals} ilk farklı karakterde döndüğü için
     * cevap süresinden imza tahmin edilebilir (timing attack).
     */
    public static boolean matches(@NotNull String expected, String actual) {
        return actual != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256Hex(byte[] body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
