/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.idempotency.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * <h1>IdempotencyProperties</h1>
 * <p>{@code application.idempotency.*} ayarları.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-2
 */
@Data
@Validated
@ConfigurationProperties(prefix = "application.idempotency")
public class IdempotencyProperties {

    /**
     * DB kaydının ömrü. POS'un en uzun retry penceresinden uzun olmalı; gün sonuna kadar void yapılabildiği için 24 saat.
     */
    @NotNull
    private Duration recordTtl = Duration.ofHours(24);

    /**
     * Redis in-flight kilidinin ömrü. Bir ödeme isteğinin işlenme süresinden biraz uzun tutulur.
     * Uygulama kilidi bırakamadan ölürse kilit en geç bu süre sonunda kendiliğinden düşer.
     */
    @NotNull
    private Duration lockTtl = Duration.ofSeconds(10);

    /**
     * İstek hash'i kart numarası içerdiği için düz SHA-256 yerine HMAC-SHA256 kullanılır (PCI DSS).
     */
    @NotBlank
    private String hashSecret;

    private int cleanupBatchSize = 1000;
}
