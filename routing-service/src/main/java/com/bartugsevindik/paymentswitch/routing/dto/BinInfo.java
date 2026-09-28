/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.routing.dto;

import com.bartugsevindik.paymentswitch.common.enums.BankCode;
import com.bartugsevindik.paymentswitch.routing.enums.CardProgram;
import com.bartugsevindik.paymentswitch.routing.enums.CardScheme;
import com.bartugsevindik.paymentswitch.routing.enums.CardType;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * <h1>BinInfo</h1>
 * <p>BIN çözümlemesinin sonucu. Cache'te bu nesne tutulur; entity tutulmaz (lazy loading, dirty checking riski).</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-4
 */
@Schema(name = "BinInfo", description = "BIN çözümleme sonucu")
public record BinInfo(
        @Schema(description = "Eşleşen BIN prefix'i", example = "540061") String binPrefix,
        @Schema(description = "Kartı çıkaran banka", example = "YKB") BankCode issuerBank,
        @Schema(description = "Taksit programı", example = "WORLD") CardProgram cardProgram,
        @Schema(description = "Kart kuruluşu", example = "MASTERCARD") CardScheme cardScheme,
        @Schema(description = "Kart tipi", example = "CREDIT") CardType cardType,
        @Schema(description = "Ticari kart mı", example = "false") boolean commercial
) {
}
