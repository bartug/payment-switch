/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.terminal.dto;

import com.bartugsevindik.paymentswitch.common.enums.TerminalType;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(name = "TerminalCreateRequest", description = "Yeni terminal tanımlama isteği")
public class TerminalCreateRequest {

    @NotBlank
    @Pattern(regexp = "^[A-Z0-9]{8,32}$", message = "Terminal numarası 8-32 karakter, büyük harf ve rakam olmalıdır.")
    @Schema(description = "Terminal numarası. Fiziki POS'ta cihaz seri numarası kullanılabilir.", example = "TRM00000001")
    private String terminalId;

    @NotBlank
    @Pattern(regexp = "^[A-Z0-9]{8,32}$", message = "Üye işyeri numarası 8-32 karakter, büyük harf ve rakam olmalıdır.")
    @Schema(description = "Üye işyeri numarası", example = "MRC0000001")
    private String merchantId;

    @NotNull
    @Schema(description = "Terminal tipi", example = "VIRTUAL", allowableValues = {"PHYSICAL", "VIRTUAL"})
    private TerminalType terminalType;
}
