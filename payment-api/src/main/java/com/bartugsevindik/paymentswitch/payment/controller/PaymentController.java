/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.controller;

import com.bartugsevindik.paymentswitch.common.dto.ResponseMessage;
import com.bartugsevindik.paymentswitch.common.helpers.ResponseHelper;
import com.bartugsevindik.paymentswitch.payment.dto.PaymentCreateRequest;
import com.bartugsevindik.paymentswitch.payment.dto.PaymentDTO;
import com.bartugsevindik.paymentswitch.payment.service.PaymentService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <h1>PaymentController</h1>
 * <p>POS terminallerinin ödeme gönderdiği ve sonucu sorguladığı uç noktaları barındırır.</p>
 * <p>Ödeme senkron olarak bankaya gitmez; istek kaydedilip {@code 202 Accepted} döner,
 * banka sonucu asenkron olarak işlenir.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-1
 */
@Tag(name = "Ödeme İşlemleri", description = "POS terminallerinden gelen ödemeler yönetilir.")
@RestController
@RequestMapping("/v1/payments")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentService paymentService;

    /**
     * <h1>Ödeme Oluştur</h1>
     * <p>POS'tan gelen ödeme isteğini karşılar ve {@code PENDING} durumunda kaydeder.</p>
     *
     * @param request Ödeme isteği
     * @return Oluşturulan ödeme
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-1
     */
    @Operation(summary = "Ödeme Oluştur",
               description = "POS terminalinden gelen ödemeyi karşılar. İşlem asenkron olarak bankaya yönlendirilir, sonuç paymentId ile sorgulanır.")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "202",
                    description = "Ödeme alındı, işleniyor.",
                    content = @Content(schema = @Schema(implementation = PaymentDTO.class))
            ),
            @ApiResponse(responseCode = "400", description = "İstek doğrulanamadı.")
    })
    @PostMapping
    public ResponseEntity<ResponseMessage> createPayment(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Ödeme isteği",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PaymentCreateRequest.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "merchantId": "MRC0000001",
                                      "terminalId": "TRM00000001",
                                      "terminalType": "VIRTUAL",
                                      "amount": 1250.50,
                                      "currency": "TRY",
                                      "installmentCount": 3,
                                      "cardNumber": "5400617020092306",
                                      "expiryMonth": "12",
                                      "expiryYear": "28",
                                      "cvv": "000"
                                    }
                                    """)
                    )
            )
            @Valid @RequestBody PaymentCreateRequest request) {
        PaymentDTO dto = paymentService.createPayment(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ResponseHelper.accepted("Ödeme alındı.", dto));
    }

    /**
     * <h1>Ödeme Getir</h1>
     * <p>Ödemenin güncel durumunu döndürür.</p>
     *
     * @param paymentId Ödeme ID
     * @return Ödeme bilgisi
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-1
     */
    @Operation(summary = "Ödeme Getir",
               description = "paymentId ile ödemenin güncel durumunu, yönlendirildiği bankayı ve banka cevabını döndürür.")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Ödeme bulundu.",
                    content = @Content(schema = @Schema(implementation = PaymentDTO.class))
            ),
            @ApiResponse(responseCode = "404", description = "Ödeme bulunamadı.")
    })
    @GetMapping("/{paymentId}")
    public ResponseEntity<ResponseMessage> getPayment(@PathVariable String paymentId) {
        PaymentDTO dto = paymentService.getPayment(paymentId);
        return ResponseEntity.ok(ResponseHelper.success("Ödeme getirildi.", dto));
    }
}
