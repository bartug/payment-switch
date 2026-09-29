/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.ledger.dto;

import com.bartugsevindik.paymentswitch.payment.ledger.enums.LedgerEntryType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(name = "JournalEntryDTO", description = "Yevmiye kaydı")
public record JournalEntryDTO(
        @Schema(description = "Kayıt ID") String entryId,
        @Schema(description = "Tip", example = "SALE") LedgerEntryType entryType,
        @Schema(description = "Kaydı oluşturan olay (ödeme ya da iade/iptal işlemi)") String referenceId,
        @Schema(description = "Kayıt zamanı") LocalDateTime createdDate,
        @Schema(description = "Satırlar") List<JournalLineDTO> lines
) {
}
