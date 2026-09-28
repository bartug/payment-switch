/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.vault.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * <h1>CardVaultProperties</h1>
 * <p>{@code application.card-vault.*} ayarları.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-5
 */
@Data
@Validated
@ConfigurationProperties(prefix = "application.card-vault")
public class CardVaultProperties {

    /**
     * Kart verisini şifreleyen AES-256 anahtarı (Base64, 32 byte). Terminal secret anahtarından farklı olmalı.
     */
    @NotBlank
    private String encryptionKey;

    /**
     * Kart verisinin en fazla ne kadar saklanacağı. Banka cevabı gelince beklemeden silinir; bu süre,
     * hiç bankaya gidemeyen ödemelerin kart verisinin temizlenmesi içindir.
     */
    @NotNull
    private Duration ttl = Duration.ofMinutes(15);

    /**
     * {@code /internal/**} uç noktaları için servisler arası token. Production'da mTLS / service mesh ile değiştirilmeli.
     */
    @NotBlank
    private String internalApiToken;
}
