/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.webhook.controller;

import com.bartugsevindik.paymentswitch.common.dto.ResponseMessage;
import com.bartugsevindik.paymentswitch.common.helpers.ResponseHelper;
import com.bartugsevindik.paymentswitch.payment.webhook.dto.WebhookDeliveryDTO;
import com.bartugsevindik.paymentswitch.payment.webhook.service.WebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * <h1>WebhookAdminController</h1>
 * <p>Webhook teslimatlarını izleme ve tekrar gönderme. Operasyon ekibi içindir.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 29.09.2026 - PS-6
 */
@Tag(name = "Webhook Yönetimi", description = "Üye işyeri bildirimleri izlenir ve tekrar gönderilir.")
@RestController
@RequestMapping("/v1/admin/webhooks")
@RequiredArgsConstructor
public class WebhookAdminController {
    private final WebhookService webhookService;

    /**
     * <h1>Ödemenin Bildirimlerini Listele</h1>
     *
     * @param paymentId Ödeme ID
     * @return Teslimat kayıtları
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 29.09.2026 - PS-6
     */
    @Operation(summary = "Ödemenin Bildirimlerini Listele", description = "Üye işyeri \"bildirim gelmedi\" dediğinde teslimat geçmişine bakmak için.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Kayıtlar getirildi.",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = WebhookDeliveryDTO.class))))
    })
    @GetMapping
    public ResponseEntity<ResponseMessage> getDeliveries(@RequestParam String paymentId) {
        return ResponseEntity.ok(ResponseHelper.success("Bildirimler getirildi.", webhookService.getDeliveries(paymentId)));
    }

    /**
     * <h1>Bildirimi Tekrar Gönder</h1>
     *
     * @param deliveryId Bildirim ID
     * @return Güncel teslimat kaydı
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 29.09.2026 - PS-6
     */
    @Operation(summary = "Bildirimi Tekrar Gönder", description = "Tüm denemeleri biten (FAILED) bildirimi yeniden kuyruğa alır. Aynı bildirim ID'si ile gider.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Tekrar gönderilmek üzere kuyruğa alındı.",
                    content = @Content(schema = @Schema(implementation = WebhookDeliveryDTO.class))),
            @ApiResponse(responseCode = "404", description = "Bildirim bulunamadı.")
    })
    @PostMapping("/{deliveryId}/redeliver")
    public ResponseEntity<ResponseMessage> redeliver(@PathVariable String deliveryId) {
        return ResponseEntity.ok(ResponseHelper.success("Bildirim tekrar gönderilecek.", webhookService.redeliver(deliveryId)));
    }
}
