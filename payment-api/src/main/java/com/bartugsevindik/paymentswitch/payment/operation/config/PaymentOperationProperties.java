/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.operation.config;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.LocalTime;
import java.time.ZoneId;

/**
 * <h1>PaymentOperationProperties</h1>
 * <p>{@code application.payment-operation.*} ayarları.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 29.09.2026 - PS-6
 */
@Data
@Validated
@ConfigurationProperties(prefix = "application.payment-operation")
public class PaymentOperationProperties {

    /**
     * Gün sonu saati. Bu saatten sonra o günün işlemleri iptal edilemez, sadece iade edilebilir.
     * Bankaların cut-off saatinden bir miktar önce tutulur; iptal isteğinin bankaya zamanında ulaşması için.
     */
    @NotNull
    private LocalTime businessDayCutoff = LocalTime.of(23, 30);

    @NotNull
    private ZoneId businessZone = ZoneId.of("Europe/Istanbul");
}
