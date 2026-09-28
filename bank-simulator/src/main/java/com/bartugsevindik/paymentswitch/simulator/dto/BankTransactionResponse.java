/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.simulator.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * <h1>BankTransactionResponse</h1>
 * <p>Bankanın satış, sorgulama ve iptal cevabı.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-5
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "BankTransactionResponse", description = "Banka işlem cevabı")
public record BankTransactionResponse(
        @Schema(description = "Sipariş numarası") String orderId,
        @Schema(description = "İşlem durumu", allowableValues = {"APPROVED", "DECLINED", "REVERSED"}) String status,
        @Schema(description = "Cevap kodu (ISO 8583 DE39)", example = "00") String responseCode,
        @Schema(description = "Onay kodu (DE38)", example = "482915") String authCode,
        @Schema(description = "Referans numarası (DE37)", example = "627104839215") String rrn,
        @Schema(description = "Açıklama", example = "Onaylandı") String message
) {
}
