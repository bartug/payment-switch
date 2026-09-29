/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.ledger.dto;

import com.bartugsevindik.paymentswitch.payment.ledger.enums.EntryDirection;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(name = "JournalLineDTO", description = "Yevmiye satırı")
public record JournalLineDTO(
        @Schema(description = "Hesap", example = "MERCHANT_PAYABLE:MRC0000001") String accountCode,
        @Schema(description = "Yön", example = "CREDIT") EntryDirection direction,
        @Schema(description = "Tutar", example = "975.10") BigDecimal amount,
        @Schema(description = "Para birimi", example = "TRY") String currency
) {
}
