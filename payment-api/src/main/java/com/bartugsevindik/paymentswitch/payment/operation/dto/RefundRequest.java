/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.operation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "RefundRequest", description = "İade isteği")
public class RefundRequest {

    @NotNull
    @DecimalMin(value = "0.01", message = "İade tutarı sıfırdan büyük olmalıdır.")
    @Digits(integer = 12, fraction = 2)
    @Schema(description = "İade tutarı. Ödemenin tamamı ya da bir kısmı.", example = "500.00")
    private BigDecimal amount;
}
