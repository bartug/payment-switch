/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.simulator.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * <h1>BankOperationRequest</h1>
 * <p>İptal ya da iade isteği. {@code operationId} bankada tekildir; aynı ID ile tekrar gelen istek ikinci kez işlenmez.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 29.09.2026 - PS-6
 */
@Schema(name = "BankOperationRequest", description = "İptal / iade isteği")
public record BankOperationRequest(
        @NotBlank @Schema(description = "İşlem ID, bankada tekil") String operationId,
        @Min(1) @Schema(description = "İade tutarı (kuruş). İptalde kullanılmaz.", example = "50000") Long amount
) {
}
