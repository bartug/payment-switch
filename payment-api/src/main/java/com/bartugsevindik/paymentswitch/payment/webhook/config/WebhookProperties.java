/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.webhook.config;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/**
 * <h1>WebhookProperties</h1>
 * <p>{@code application.webhook.*} ayarları.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 29.09.2026 - PS-6
 */
@Data
@Validated
@ConfigurationProperties(prefix = "application.webhook")
public class WebhookProperties {

    /**
     * Başarısız denemeler arası bekleme. Liste bitince teslimat {@code FAILED} olur.
     * Varsayılan toplam ~11 saat: üye işyerinin kısa kesintilerini ve bir gecelik bakımını tolere eder.
     */
    @NotEmpty
    private List<Duration> retrySchedule = List.of(Duration.ofSeconds(10), Duration.ofSeconds(30), Duration.ofMinutes(2),
            Duration.ofMinutes(10), Duration.ofMinutes(30), Duration.ofHours(1), Duration.ofHours(3), Duration.ofHours(6));

    @NotNull
    private Duration connectTimeout = Duration.ofSeconds(2);

    /**
     * Üye işyeri bu sürede 2xx dönmezse deneme başarısız sayılır. Bildirimi alıp işi arka planda yapması beklenir.
     */
    @NotNull
    private Duration readTimeout = Duration.ofSeconds(5);

    private int batchSize = 50;
}
