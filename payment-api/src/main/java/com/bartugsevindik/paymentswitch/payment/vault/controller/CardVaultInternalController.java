/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.vault.controller;

import com.bartugsevindik.paymentswitch.common.dto.ResponseMessage;
import com.bartugsevindik.paymentswitch.common.helpers.ResponseHelper;
import com.bartugsevindik.paymentswitch.payment.vault.dto.CardData;
import com.bartugsevindik.paymentswitch.payment.vault.service.CardVaultService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <h1>CardVaultInternalController</h1>
 * <p>bank-adapter'ın kart verisini bankaya göndermeden hemen önce çözdürdüğü uç nokta.</p>
 * <p>GET değil POST: kart verisi dönen bir isteğin proxy, CDN ya da tarayıcı tarafından cache'lenmemesi için.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-5
 */
@Tag(name = "Card Vault (internal)", description = "Sadece servisler arası. Dışarıya açılmaz, X-Internal-Token zorunlu.")
@RestController
@RequestMapping("/internal/v1/card-vault")
@RequiredArgsConstructor
public class CardVaultInternalController {
    private final CardVaultService cardVaultService;

    /**
     * <h1>Kart Verisini Çöz</h1>
     * <p>Token'a ait kart verisini döndürür.</p>
     *
     * @param token Kart token'ı
     * @return Kart verisi
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-5
     */
    @Operation(summary = "Kart Verisini Çöz",
               description = "bank-adapter'ın bankaya göndermeden önce çağırdığı uç nokta. Banka cevabı geldikten sonra kart verisi silinir ve token geçersiz olur.")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Kart verisi döndü.",
                    content = @Content(schema = @Schema(implementation = CardData.class))
            ),
            @ApiResponse(responseCode = "401", description = "X-Internal-Token geçersiz."),
            @ApiResponse(responseCode = "404", description = "Token bulunamadı ya da süresi doldu.")
    })
    @PostMapping("/{token}/detokenize")
    public ResponseEntity<ResponseMessage> detokenize(@PathVariable String token) {
        return ResponseEntity.ok(ResponseHelper.success("Kart verisi getirildi.", cardVaultService.detokenize(token)));
    }
}
