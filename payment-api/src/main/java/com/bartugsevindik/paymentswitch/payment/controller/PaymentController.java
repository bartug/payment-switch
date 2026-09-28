/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.controller;

import com.bartugsevindik.paymentswitch.common.dto.ResponseMessage;
import com.bartugsevindik.paymentswitch.common.helpers.ResponseHelper;
import com.bartugsevindik.paymentswitch.payment.dto.PaymentCreateRequest;
import com.bartugsevindik.paymentswitch.payment.dto.PaymentDTO;
import com.bartugsevindik.paymentswitch.payment.idempotency.dto.IdempotentResult;
import com.bartugsevindik.paymentswitch.payment.service.PaymentService;
import com.bartugsevindik.paymentswitch.payment.terminal.security.TerminalPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    public static final String IDEMPOTENT_REPLAYED_HEADER = "Idempotent-Replayed";

    private final PaymentService paymentService;

    /**
     * <h1>Ödeme Oluştur</h1>
     * <p>POS'tan gelen ödeme isteğini karşılar ve {@code PENDING} durumunda kaydeder.
     * Aynı {@code Idempotency-Key} ile tekrar gelirse yeni ödeme oluşturmaz.</p>
     *
     * @param terminal       İmzası doğrulanmış terminal
     * @param idempotencyKey Ödeme denemesi başına üretilen key
     * @param request        Ödeme isteği
     * @return Oluşturulan ödeme
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-1
     */
    @Operation(summary = "Ödeme Oluştur",
               description = """
                       POS terminalinden gelen ödemeyi karşılar. İşlem asenkron olarak bankaya yönlendirilir, sonuç paymentId ile sorgulanır.
                       Ağ hatası sonrası aynı istek **aynı Idempotency-Key** ile tekrar gönderilmelidir; yeni ödeme oluşmaz, mevcut ödemenin güncel hali döner.
                       """)
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "202",
                    description = "Ödeme alındı, işleniyor. Tekrar eden istekte `Idempotent-Replayed: true` header'ı döner.",
                    content = @Content(schema = @Schema(implementation = PaymentDTO.class)),
                    headers = @Header(name = IDEMPOTENT_REPLAYED_HEADER, description = "İstek daha önce işlendiyse true",
                            schema = @Schema(type = "boolean"))
            ),
            @ApiResponse(responseCode = "400", description = "İstek doğrulanamadı ya da Idempotency-Key eksik."),
            @ApiResponse(responseCode = "401", description = "Terminal imzası doğrulanamadı ya da zaman damgası geçersiz."),
            @ApiResponse(responseCode = "403", description = "Terminal işlem almaya kapalı."),
            @ApiResponse(responseCode = "409", description = "Aynı key ile gelen istek hâlâ işleniyor. Retry-After kadar bekleyip tekrar deneyin."),
            @ApiResponse(responseCode = "422", description = "Idempotency-Key daha önce farklı bir istek ile kullanılmış.")
    })
    @PostMapping
    public ResponseEntity<ResponseMessage> createPayment(
            @Parameter(hidden = true) @RequestAttribute(TerminalPrincipal.REQUEST_ATTRIBUTE) TerminalPrincipal terminal,
            @Parameter(description = "Ödeme denemesi başına üretilen tekil key (UUID önerilir). Retry'da aynı key gönderilmelidir.",
                       required = true, example = "7c9e6679-7425-40de-944b-e07fc1f90ae7")
            @RequestHeader(IDEMPOTENCY_KEY_HEADER)
            @Pattern(regexp = "^[A-Za-z0-9_-]{8,64}$", message = "Idempotency-Key 8-64 karakter olmalı ve sadece harf, rakam, '-' ve '_' içermelidir.")
            String idempotencyKey,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Ödeme isteği",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PaymentCreateRequest.class),
                            examples = @ExampleObject(value = """
                                    {
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
        IdempotentResult<PaymentDTO> result = paymentService.createPayment(terminal, idempotencyKey, request);
        String message = result.replayed() ? "Ödeme daha önce alındı." : "Ödeme alındı.";
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .header(IDEMPOTENT_REPLAYED_HEADER, String.valueOf(result.replayed()))
                .body(ResponseHelper.accepted(message, result.body()));
    }

    /**
     * <h1>Ödeme Getir</h1>
     * <p>Ödemenin güncel durumunu döndürür.</p>
     *
     * @param terminal  İmzası doğrulanmış terminal
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
            @ApiResponse(responseCode = "401", description = "Terminal imzası doğrulanamadı."),
            @ApiResponse(responseCode = "404", description = "Ödeme bulunamadı ya da terminalin üye işyerine ait değil.")
    })
    @GetMapping("/{paymentId}")
    public ResponseEntity<ResponseMessage> getPayment(
            @Parameter(hidden = true) @RequestAttribute(TerminalPrincipal.REQUEST_ATTRIBUTE) TerminalPrincipal terminal,
            @PathVariable String paymentId) {
        PaymentDTO dto = paymentService.getPayment(terminal, paymentId);
        return ResponseEntity.ok(ResponseHelper.success("Ödeme getirildi.", dto));
    }
}
