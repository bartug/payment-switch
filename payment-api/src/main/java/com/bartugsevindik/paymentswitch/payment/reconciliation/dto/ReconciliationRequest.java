/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.reconciliation.dto;

import com.bartugsevindik.paymentswitch.common.enums.BankCode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ReconciliationRequest", description = "Mutabakat isteği")
public class ReconciliationRequest {

    @NotNull
    @Schema(description = "Banka", example = "YKB")
    private BankCode bankCode;

    @NotNull
    @Schema(description = "İş günü", example = "2026-09-29")
    private LocalDate businessDate;
}
