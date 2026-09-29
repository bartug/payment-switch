/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.ledger.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(name = "TrialBalanceDTO", description = "Mizan: tüm kayıtların borç ve alacak toplamı")
public record TrialBalanceDTO(
        @Schema(description = "Toplam borç") BigDecimal totalDebit,
        @Schema(description = "Toplam alacak") BigDecimal totalCredit,
        @Schema(description = "Borç = alacak mı", example = "true") boolean balanced
) {
}
