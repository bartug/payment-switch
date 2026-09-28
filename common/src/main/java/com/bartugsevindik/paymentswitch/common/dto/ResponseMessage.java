/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

@Schema(name = "Global Response Object", description = "Tüm cevapların taşıyıcısı. Başarılı ya da başarısız.")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ResponseMessage {

    @Schema(description = "İşlem başarılı mı?",
            type = "Boolean",
            example = "true")
    private Boolean success;

    @Schema(description = "Varsa anlamlı cevap mesajı.",
            type = "String",
            example = "Ödeme alındı. / Kart numarası geçersiz.")
    private String message;

    @Schema(description = "Varsa cevap objesi.",
            type = "Object",
            example = "{}", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Object object;

    @Schema(description = "Http status.", type = "String", example = "OK")
    private HttpStatus httpStatus;

    @Schema(description = "Http status code.", type = "Integer", example = "200")
    private Integer httpStatusCode;
}
