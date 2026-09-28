/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.terminal.security;

import com.bartugsevindik.paymentswitch.common.crypto.AesGcmCipher;
import com.bartugsevindik.paymentswitch.payment.terminal.config.TerminalAuthProperties;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

/**
 * <h1>TerminalSecretCipher</h1>
 * <p>Terminal secret'larını AES-256-GCM ile şifreler. Card vault'tan farklı bir anahtar kullanır;
 * bir anahtarın sızması diğer verileri açığa çıkarmaz.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-2
 */
@Component
public class TerminalSecretCipher {

    private final AesGcmCipher cipher;

    public TerminalSecretCipher(TerminalAuthProperties properties) {
        this.cipher = new AesGcmCipher(properties.getSecretMasterKey());
    }

    public String encrypt(@NotNull String plainSecret) {
        return cipher.encrypt(plainSecret);
    }

    public String decrypt(@NotNull String ciphertext) {
        return cipher.decrypt(ciphertext);
    }
}
