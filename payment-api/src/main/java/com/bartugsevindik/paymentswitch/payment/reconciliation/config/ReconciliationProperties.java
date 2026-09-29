/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.reconciliation.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * <h1>ReconciliationProperties</h1>
 * <p>{@code application.reconciliation.*} ayarları.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 29.09.2026 - PS-7
 */
@Data
@Validated
@ConfigurationProperties(prefix = "application.reconciliation")
public class ReconciliationProperties {

    /**
     * Bankaların gün sonu dosyalarının alındığı adres. Gerçekte her banka için SFTP ya da API; burada simülatör.
     */
    @NotBlank
    private String bankFileBaseUrl;

    /**
     * Eşleşmeyen kayıt için önceki ve sonraki kaç günün dosyasına bakılacağı (cut-off kayması).
     */
    private int dateToleranceDays = 1;
}
