/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.terminal.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * <h1>TerminalAuthProperties</h1>
 * <p>{@code application.terminal-auth.*} ayarları.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-2
 */
@Data
@Validated
@ConfigurationProperties(prefix = "application.terminal-auth")
public class TerminalAuthProperties {

    /**
     * Terminal saati ile sunucu saati arasındaki izin verilen fark. Bu pencerenin dışındaki istekler reddedilir.
     * Pencere içindeki tekrarlar idempotency ile yakalanır, bu yüzden ayrı bir nonce deposu tutulmaz.
     */
    @NotNull
    private Duration timestampTolerance = Duration.ofMinutes(5);

    /**
     * Terminal secret'larını DB'de şifrelemek için AES-256 anahtarı (Base64, 32 byte).
     * Production'da KMS / Vault'tan gelmeli.
     */
    @NotBlank
    private String secretMasterKey;

    /**
     * İmza doğrulaması için body belleğe alındığından üst sınır konur.
     */
    private int maxBodyBytes = 16 * 1024;
}
