/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.terminal.security;

import com.bartugsevindik.paymentswitch.payment.terminal.config.TerminalAuthProperties;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * <h1>TerminalSecretCipher</h1>
 * <p>Terminal secret'larını AES-256-GCM ile şifreler. Çıktı formatı: {@code v1:Base64(iv || ciphertext+tag)}.</p>
 * <p>{@code v1} ön eki anahtar versiyonudur. Master key değiştirildiğinde eski kayıtlar eski anahtarla çözülüp
 * yeni anahtarla şifrelenene kadar iki versiyon birlikte desteklenebilir.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-2
 */
@Component
public class TerminalSecretCipher {

    private static final String KEY_VERSION = "v1:";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKeySpec masterKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public TerminalSecretCipher(TerminalAuthProperties properties) {
        byte[] key = Base64.getDecoder().decode(properties.getSecretMasterKey());
        if (key.length != 32) {
            throw new IllegalStateException("Terminal secret master key must be 32 bytes (AES-256)");
        }
        this.masterKey = new SecretKeySpec(key, "AES");
    }

    public String encrypt(@NotNull String plainSecret) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainSecret.getBytes(StandardCharsets.UTF_8));
            byte[] payload = ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
            return KEY_VERSION + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Terminal secret could not be encrypted", e);
        }
    }

    public String decrypt(@NotNull String ciphertext) {
        if (!ciphertext.startsWith(KEY_VERSION)) {
            throw new IllegalStateException("Unsupported terminal secret key version");
        }
        try {
            ByteBuffer payload = ByteBuffer.wrap(Base64.getDecoder().decode(ciphertext.substring(KEY_VERSION.length())));
            byte[] iv = new byte[IV_LENGTH];
            payload.get(iv);
            byte[] encrypted = new byte[payload.remaining()];
            payload.get(encrypted);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, masterKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Terminal secret could not be decrypted", e);
        }
    }
}
