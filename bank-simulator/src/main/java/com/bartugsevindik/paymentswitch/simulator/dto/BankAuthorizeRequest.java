/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.simulator.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * <h1>BankAuthorizeRequest</h1>
 * <p>Bankanın sanal POS satış isteği. {@code orderId} bankada tekildir; aynı orderId ile tekrar gelen istek
 * yeni işlem oluşturmaz, ilk sonucu döner (banka tarafı idempotency).</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-5
 */
@Schema(name = "BankAuthorizeRequest", description = "Banka satış isteği")
public record BankAuthorizeRequest(
        @NotBlank @Schema(description = "Üye işyerinin sipariş numarası, bankada tekil", example = "8f14e45f-ceea-4e7a-9d1c-5b6a3f0e2c11") String orderId,
        @NotBlank @Schema(description = "Kart numarası", example = "5400617020092306") String pan,
        @NotBlank @Schema(description = "Son kullanma ayı", example = "12") String expiryMonth,
        @NotBlank @Schema(description = "Son kullanma yılı", example = "28") String expiryYear,
        @Schema(description = "CVV", example = "000") String cvv,
        @NotNull @Min(1) @Schema(description = "Tutar (kuruş)", example = "125050") Long amount,
        @NotBlank @Schema(description = "Para birimi", example = "TRY") String currency,
        @NotNull @Min(1) @Schema(description = "Taksit sayısı", example = "3") Integer installmentCount
) {

    @Override
    public String toString() {
        return "BankAuthorizeRequest[orderId=" + orderId + ", amount=" + amount + ", installment=" + installmentCount + "]";
    }
}
