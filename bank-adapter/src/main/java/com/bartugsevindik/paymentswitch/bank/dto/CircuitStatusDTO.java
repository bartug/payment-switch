/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.bank.dto;

import com.bartugsevindik.paymentswitch.common.enums.BankCode;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "CircuitStatusDTO", description = "Bankanın circuit breaker durumu")
public record CircuitStatusDTO(
        @Schema(description = "Banka", example = "YKB") BankCode bankCode,
        @Schema(description = "Durum", example = "CLOSED") String state,
        @Schema(description = "Son çağrılardaki hata oranı (%)", example = "0.0") float failureRate,
        @Schema(description = "Pencere içindeki çağrı sayısı", example = "10") int bufferedCalls,
        @Schema(description = "Bulkhead'de boş yer", example = "20") int availableConcurrentCalls
) {
}
