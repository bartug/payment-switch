/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.loadtest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;

/**
 * <h1>PosRequestSigner</h1>
 * <p>Terminal imzası; docs/03-terminal-kimlik-dogrulama.md'deki algoritmanın bağımsız bir uygulaması. payment-api'nin
 * kodunu kullanmaz; bir POS SDK'sının dokümana bakarak yazacağı kodla aynıdır.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 29.09.2026 - PS-8
 */
final class PosRequestSigner {

    private PosRequestSigner() {
    }

    static String sign(String secret, String method, String path, String timestamp, String idempotencyKey, String body) {
        try {
            String bodyHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(body.getBytes(StandardCharsets.UTF_8)));
            String stringToSign = String.join("\n", method, path, timestamp, idempotencyKey == null ? "" : idempotencyKey, bodyHash);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
