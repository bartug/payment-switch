/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.routing.config;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * <h1>RoutingProperties</h1>
 * <p>{@code application.routing.*} ayarları.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-4
 */
@Data
@Validated
@ConfigurationProperties(prefix = "application.routing")
public class RoutingProperties {

    /**
     * BIN tablosu nadiren değişir; her ödemede DB'ye gitmemek için cache'lenir.
     */
    @NotNull
    private Duration binCacheTtl = Duration.ofHours(1);

    private long binCacheSize = 100_000;

    /**
     * Banka aktif/pasif bilgisinin cache süresi. Bir pod'da pasife alınan banka diğer pod'lara en geç bu sürede yansır.
     */
    @NotNull
    private Duration bankCacheTtl = Duration.ofSeconds(5);
}
