/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.ledger.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * <h1>LedgerProperties</h1>
 * <p>{@code application.ledger.*} ayarları.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 29.09.2026 - PS-7
 */
@Data
@Validated
@ConfigurationProperties(prefix = "application.ledger")
public class LedgerProperties {

    /**
     * Üye işyeri tanımlı değilse uygulanacak komisyon (baz puan).
     */
    @Min(0)
    @Max(10_000)
    private int defaultCommissionRateBps = 249;
}
