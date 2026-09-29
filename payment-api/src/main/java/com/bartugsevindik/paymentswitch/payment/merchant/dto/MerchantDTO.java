/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.merchant.dto;

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
@Schema(name = "MerchantDTO", description = "Üye işyeri bilgisi")
public class MerchantDTO {

    @Schema(description = "Üye işyeri numarası", example = "MRC0000001")
    private String merchantId;

    @Schema(description = "Üye işyeri adı", example = "Taksici Ahmet")
    private String name;

    @Schema(description = "Webhook adresi")
    private String webhookUrl;

    @Schema(description = "Webhook imzalarını doğrulamak için secret. <b>Sadece oluşturma ve yenilemede bir kez döner.</b>")
    private String webhookSecret;
}
