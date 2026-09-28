/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.routing.dto;

import com.bartugsevindik.paymentswitch.common.enums.BankCode;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * <h1>AcquirerBankInfo</h1>
 * <p>İşlem gönderilebilen banka ve komisyon oranları.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-4
 */
@Schema(name = "AcquirerBankInfo", description = "İşlem gönderilebilen banka")
public record AcquirerBankInfo(
        @Schema(description = "Banka kodu", example = "YKB") BankCode bankCode,
        @Schema(description = "Operasyon tarafından aktif mi", example = "true") boolean active,
        @Schema(description = "Circuit breaker'a göre sağlıklı mı (otomatik)", example = "true") boolean healthy,
        @Schema(description = "On-us komisyon (baz puan)", example = "180") int onUsRateBps,
        @Schema(description = "Off-us komisyon (baz puan)", example = "220") int offUsRateBps
) {

    public boolean canReceive() {
        return active && healthy;
    }

    public int rateFor(BankCode issuerBank) {
        return bankCode == issuerBank ? onUsRateBps : offUsRateBps;
    }
}
