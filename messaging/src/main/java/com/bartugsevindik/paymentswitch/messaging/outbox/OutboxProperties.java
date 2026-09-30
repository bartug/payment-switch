/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.messaging.outbox;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * <h1>OutboxProperties</h1>
 * <p>{@code application.outbox.*} ayarları.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-3
 */
@Data
@Validated
@ConfigurationProperties(prefix = "application.outbox")
public class OutboxProperties {

    /**
     * Relay'in yeni event'lere bakma aralığı. Ödemenin bankaya gitmesindeki ek gecikmenin üst sınırı budur.
     */
    @NotNull
    private Duration pollInterval = Duration.ofMillis(200);

    @Min(1)
    private int batchSize = 100;

    /**
     * Kafka'nın bir batch'i onaylaması için beklenecek en uzun süre. Bu süre boyunca batch'in DB kilidi tutulur.
     */
    @NotNull
    private Duration sendTimeout = Duration.ofSeconds(5);

    /**
     * Gönderilmiş event'lerin saklanma süresi. Olay incelemesi (replay, debug) için bir süre tutulur.
     */
    @NotNull
    private Duration retention = Duration.ofDays(7);

    private int cleanupBatchSize = 1000;

    /**
     * Commit sonrası relay'i beklemeden çalıştır. Testlerde kapatılır; relay elle tetiklenir.
     */
    private boolean wakeUpOnCommit = true;
}
