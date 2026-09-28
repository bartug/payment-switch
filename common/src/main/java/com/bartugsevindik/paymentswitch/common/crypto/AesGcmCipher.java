/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.common.crypto;

import org.jetbrains.annotations.NotNull;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * <h1>AesGcmCipher</h1>
 * <p>AES-256-GCM ile şifreleme. Çıktı formatı: {@code v1:Base64(iv || ciphertext+tag)}.</p>
 * <p>GCM hem gizlilik hem bütünlük sağlar; ciphertext'in tek bir biti değişse çözme başarısız olur.
 * Her şifrelemede yeni rastgele IV üretilir, aynı IV aynı anahtarla iki kez kullanılırsa GCM güvenliği çöker.</p>
 * <p>{@code v1:} ön eki anahtar versiyonudur; anahtar rotasyonunda eski ve yeni kayıtlar ayırt edilir.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-5
 */
public class AesGcmCipher {

    private static final String KEY_VERSION = "v1:";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * @param base64Key Base64 kodlu 32 byte anahtar
     */
    public AesGcmCipher(@NotNull String base64Key) {
        byte[] raw = Base64.getDecoder().decode(base64Key);
        if (raw.length != 32) {
            throw new IllegalStateException("AES-256 key must be 32 bytes");
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    public String encrypt(@NotNull String plainText) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] payload = ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
            return KEY_VERSION + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Value could not be encrypted", e);
        }
    }

    public String decrypt(@NotNull String cipherText) {
        if (!cipherText.startsWith(KEY_VERSION)) {
            throw new IllegalStateException("Unsupported key version");
        }
        try {
            ByteBuffer payload = ByteBuffer.wrap(Base64.getDecoder().decode(cipherText.substring(KEY_VERSION.length())));
            byte[] iv = new byte[IV_LENGTH];
            payload.get(iv);
            byte[] encrypted = new byte[payload.remaining()];
            payload.get(encrypted);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Value could not be decrypted", e);
        }
    }
}
