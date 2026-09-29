/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.ledger.dto;

import com.bartugsevindik.paymentswitch.payment.ledger.enums.AccountType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(name = "AccountBalanceDTO", description = "Hesap bakiyesi")
public record AccountBalanceDTO(
        @Schema(description = "Hesap", example = "MERCHANT_PAYABLE:MRC0000001") String accountCode,
        @Schema(description = "Hesap tipi", example = "LIABILITY") AccountType accountType,
        @Schema(description = "Para birimi", example = "TRY") String currency,
        @Schema(description = "Borç toplamı") BigDecimal totalDebit,
        @Schema(description = "Alacak toplamı") BigDecimal totalCredit,
        @Schema(description = "Bakiye. Varlıkta borç - alacak, borç ve gelir hesaplarında alacak - borç.", example = "975.10") BigDecimal balance
) {
}
