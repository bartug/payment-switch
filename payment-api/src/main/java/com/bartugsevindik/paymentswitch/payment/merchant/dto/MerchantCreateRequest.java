/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.merchant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "MerchantCreateRequest", description = "Üye işyeri tanımlama isteği")
public class MerchantCreateRequest {

    @NotBlank
    @Pattern(regexp = "^[A-Z0-9]{8,32}$", message = "Üye işyeri numarası 8-32 karakter, büyük harf ve rakam olmalıdır.")
    @Schema(description = "Üye işyeri numarası", example = "MRC0000001")
    private String merchantId;

    @NotBlank
    @Size(max = 128)
    @Schema(description = "Üye işyeri adı", example = "Taksici Ahmet")
    private String name;

    @Pattern(regexp = "^https?://.+", message = "Webhook adresi http(s) ile başlamalıdır.")
    @Size(max = 512)
    @Schema(description = "Ödeme sonuçlarının bildirileceği adres. Production'da sadece https kabul edilmeli.",
            example = "https://merchant.example.com/webhooks/payments")
    private String webhookUrl;
}
