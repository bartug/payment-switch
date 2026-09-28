/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.routing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "RoutingSimulationRequest", description = "Routing kararını ödeme oluşturmadan denemek için istek")
public class RoutingSimulationRequest {

    @NotBlank
    @Pattern(regexp = "^\\d{6,8}$", message = "BIN 6-8 haneli olmalıdır.")
    @Schema(description = "Kartın ilk 6 ya da 8 hanesi", example = "54006170")
    private String cardBin;

    @NotNull
    @Min(1)
    @Max(12)
    @Schema(description = "Taksit sayısı. 1 = tek çekim", example = "3")
    private Integer installmentCount;
}
