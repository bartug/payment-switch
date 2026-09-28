/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.bank.dto;

import com.bartugsevindik.paymentswitch.bank.enums.BankTransactionStatus;
import com.bartugsevindik.paymentswitch.common.enums.BankCode;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "BankTransactionDTO", description = "Bankaya gönderilen işlem")
public record BankTransactionDTO(
        @Schema(description = "Ödeme ID") String paymentId,
        @Schema(description = "Bankaya giden sipariş numarası") String orderId,
        @Schema(description = "Banka", example = "YKB") BankCode bankCode,
        @Schema(description = "Durum", example = "APPROVED") BankTransactionStatus status,
        @Schema(description = "Banka cevap kodu", example = "00") String responseCode,
        @Schema(description = "Onay kodu") String authCode,
        @Schema(description = "Banka referans numarası") String rrn,
        @Schema(description = "Açıklama") String message,
        @Schema(description = "Inquiry / reversal deneme sayısı") Integer attempts,
        @Schema(description = "Son hata") String lastError,
        @Schema(description = "Oluşturulma zamanı") LocalDateTime createdDate
) {
}
