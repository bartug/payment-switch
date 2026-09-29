/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.merchant.controller;

import com.bartugsevindik.paymentswitch.common.dto.ResponseMessage;
import com.bartugsevindik.paymentswitch.common.helpers.ResponseHelper;
import com.bartugsevindik.paymentswitch.payment.merchant.dto.MerchantCreateRequest;
import com.bartugsevindik.paymentswitch.payment.merchant.dto.MerchantDTO;
import com.bartugsevindik.paymentswitch.payment.merchant.service.MerchantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <h1>MerchantController</h1>
 * <p>Üye işyeri tanımlama ve webhook secret yönetimi. Operasyon ekibi içindir, backoffice kimlik doğrulaması ile korunacak.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 29.09.2026 - PS-6
 */
@Tag(name = "Üye İşyeri İşlemleri", description = "Üye işyerleri ve webhook ayarları yönetilir.")
@RestController
@RequestMapping("/v1/admin/merchants")
@RequiredArgsConstructor
public class MerchantController {
    private final MerchantService merchantService;

    /**
     * <h1>Üye İşyeri Oluştur</h1>
     * <p>Üye işyerini tanımlar ve webhook secret'ını bir kez döndürür.</p>
     *
     * @param request Üye işyeri bilgileri
     * @return Secret'ı içeren üye işyeri bilgisi
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 29.09.2026 - PS-6
     */
    @Operation(summary = "Üye İşyeri Oluştur",
               description = "Dönen webhookSecret ile üye işyeri gelen bildirimlerin imzasını doğrular. Bir daha görüntülenemez.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Üye işyeri oluşturuldu.",
                    content = @Content(schema = @Schema(implementation = MerchantDTO.class))),
            @ApiResponse(responseCode = "409", description = "Üye işyeri zaten tanımlı.")
    })
    @PostMapping
    public ResponseEntity<ResponseMessage> createMerchant(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Üye işyeri bilgileri",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MerchantCreateRequest.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "merchantId": "MRC0000001",
                                      "name": "Taksici Ahmet",
                                      "webhookUrl": "https://merchant.example.com/webhooks/payments"
                                    }
                                    """)
                    )
            )
            @Valid @RequestBody MerchantCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ResponseHelper.created("Üye işyeri oluşturuldu. Webhook secret'ını güvenli bir yerde saklayın.",
                        merchantService.createMerchant(request)));
    }

    /**
     * <h1>Webhook Secret Yenile</h1>
     * <p>Yeni secret üretir, eskisi hemen geçersiz olur.</p>
     *
     * @param merchantId Üye işyeri numarası
     * @return Yeni secret'ı içeren üye işyeri bilgisi
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 29.09.2026 - PS-6
     */
    @Operation(summary = "Webhook Secret Yenile", description = "Secret sızdığında kullanılır. Eski secret ile imzalanmış bildirim gönderilmez.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Secret yenilendi.",
                    content = @Content(schema = @Schema(implementation = MerchantDTO.class))),
            @ApiResponse(responseCode = "404", description = "Üye işyeri bulunamadı.")
    })
    @PostMapping("/{merchantId}/webhook-secret")
    public ResponseEntity<ResponseMessage> rotateWebhookSecret(@PathVariable String merchantId) {
        return ResponseEntity.ok(ResponseHelper.success("Webhook secret yenilendi.", merchantService.rotateWebhookSecret(merchantId)));
    }
}
