/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.terminal.dto;

import com.bartugsevindik.paymentswitch.common.enums.TerminalType;
import com.bartugsevindik.paymentswitch.payment.terminal.enums.TerminalStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "TerminalDTO", description = "Terminal bilgisi")
public class TerminalDTO {

    @Schema(description = "Terminal numarası", example = "TRM00000001")
    private String terminalId;

    @Schema(description = "Üye işyeri numarası", example = "MRC0000001")
    private String merchantId;

    @Schema(description = "Terminal tipi", example = "VIRTUAL")
    private TerminalType terminalType;

    @Schema(description = "Terminal durumu", example = "ACTIVE")
    private TerminalStatus terminalStatus;

    @Schema(description = "İstek imzalamada kullanılacak secret. <b>Sadece terminal oluşturulurken bir kez döner</b>, tekrar görüntülenemez.",
            example = "q3JkY2x0Z2V0Y2hhbmdlbWVwbGVhc2UxMjM0NTY3OA")
    private String secret;
}
