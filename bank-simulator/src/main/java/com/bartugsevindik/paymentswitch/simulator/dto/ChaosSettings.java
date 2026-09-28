/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.simulator.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;

/**
 * <h1>ChaosSettings</h1>
 * <p>Bankanın hata davranışı. Oranlar 0 ile 1 arasıdır.</p>
 *
 * @param down             Banka tamamen kapalı: tüm istekler (echo dahil) 503 döner, işlem yapılmaz.
 * @param latencyMs        Her cevaba eklenen gecikme.
 * @param failureRate      İşlem <b>yapılmadan</b> 503 dönme oranı.
 * @param lateResponseRate İşlem <b>yapılıp</b> cevabın {@code lateResponseMs} sonra dönme oranı. Adapter timeout'a düşer
 *                         ama para çekilmiştir; "cevap gelmedi" senaryosu.
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-5
 */
@Schema(name = "ChaosSettings", description = "Bankanın hata davranışı")
public record ChaosSettings(
        @Schema(description = "Banka tamamen kapalı", example = "false") boolean down,
        @Min(0) @Schema(description = "Her cevaba eklenen gecikme (ms)", example = "0") long latencyMs,
        @DecimalMin("0") @DecimalMax("1") @Schema(description = "İşlem yapılmadan 503 dönme oranı", example = "0") double failureRate,
        @DecimalMin("0") @DecimalMax("1") @Schema(description = "İşlem yapılıp cevabın geç dönme oranı", example = "0.3") double lateResponseRate,
        @Min(0) @Schema(description = "Geç cevabın gecikmesi (ms)", example = "15000") long lateResponseMs
) {
    public static ChaosSettings none() {
        return new ChaosSettings(false, 0, 0, 0, 15_000);
    }
}
