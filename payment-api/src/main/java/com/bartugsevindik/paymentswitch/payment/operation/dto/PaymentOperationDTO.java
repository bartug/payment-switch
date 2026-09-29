/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.operation.dto;

import com.bartugsevindik.paymentswitch.common.enums.BankOperationType;
import com.bartugsevindik.paymentswitch.payment.operation.enums.PaymentOperationStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "PaymentOperationDTO", description = "İptal / iade işlemi")
public record PaymentOperationDTO(
        @Schema(description = "İşlem ID") String operationId,
        @Schema(description = "Ödeme ID") String paymentId,
        @Schema(description = "İşlem tipi", example = "REFUND") BankOperationType type,
        @Schema(description = "Tutar", example = "500.00") BigDecimal amount,
        @Schema(description = "Para birimi", example = "TRY") String currency,
        @Schema(description = "Durum. PENDING: bankadan sonuç bekleniyor.", example = "PENDING") PaymentOperationStatus status,
        @Schema(description = "Banka cevap kodu", example = "00") String responseCode,
        @Schema(description = "Başarısızsa sebebi") String failureReason,
        @Schema(description = "Oluşturulma zamanı") LocalDateTime createdDate
) {
}
