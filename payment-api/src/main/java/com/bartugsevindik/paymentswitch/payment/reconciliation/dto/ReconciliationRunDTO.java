/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.reconciliation.dto;

import com.bartugsevindik.paymentswitch.common.enums.BankCode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

@Schema(name = "ReconciliationRunDTO", description = "Mutabakat sonucu")
public record ReconciliationRunDTO(
        @Schema(description = "Çalışma ID") String runId,
        @Schema(description = "Banka", example = "YKB") BankCode bankCode,
        @Schema(description = "İş günü") LocalDate businessDate,
        @Schema(description = "Eşleşen") int matched,
        @Schema(description = "Başka günün dosyasında eşleşen") int matchedOtherDay,
        @Schema(description = "Bizde var, bankada yok") int missingInBank,
        @Schema(description = "Bankada var, bizde yok") int missingInOurs,
        @Schema(description = "Tutar farkı") int amountMismatch,
        @Schema(description = "Durum farkı") int statusMismatch,
        @Schema(description = "Aksiyon gerektiren satırlar") List<ReconciliationItemDTO> exceptions
) {
}
