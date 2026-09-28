/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.vault.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * <h1>CardData</h1>
 * <p>Bankaya gönderilecek kart verisi. {@code toString} maskelenmiştir; yanlışlıkla loglansa bile kart numarası
 * ve CVV log'a düşmez.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-5
 */
@Schema(name = "CardData", description = "Bankaya gönderilecek kart verisi")
public record CardData(
        @Schema(description = "Kart numarası", example = "5400617020092306") String pan,
        @Schema(description = "Son kullanma ayı", example = "12") String expiryMonth,
        @Schema(description = "Son kullanma yılı", example = "28") String expiryYear,
        @Schema(description = "CVV. Fiziki POS işlemlerinde boş.", example = "000") String cvv
) {

    @Override
    public String toString() {
        return "CardData[pan=" + pan.substring(0, 6) + "******" + pan.substring(pan.length() - 4) + ", cvv=***]";
    }
}
