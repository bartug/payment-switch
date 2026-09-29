/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.reconciliation.dto;

import com.bartugsevindik.paymentswitch.payment.reconciliation.enums.ReconciliationResult;
import com.bartugsevindik.paymentswitch.payment.reconciliation.enums.SettlementRecordType;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "ReconciliationItemDTO", description = "Mutabakat satırı")
public record ReconciliationItemDTO(
        @Schema(description = "Kayıt tipi", example = "SALE") SettlementRecordType recordType,
        @Schema(description = "Sipariş numarası (ödeme ID)") String orderId,
        @Schema(description = "İade işlemi ID") String operationId,
        @Schema(description = "Sonuç", example = "STATUS_MISMATCH") ReconciliationResult result,
        @Schema(description = "Bizdeki tutar") BigDecimal ourAmount,
        @Schema(description = "Bankadaki tutar") BigDecimal bankAmount,
        @Schema(description = "Bizdeki durum", example = "REVERSED") String ourStatus,
        @Schema(description = "Açıklama") String detail
) {
}
