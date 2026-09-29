/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.operation.controller;

import com.bartugsevindik.paymentswitch.common.dto.ResponseMessage;
import com.bartugsevindik.paymentswitch.common.helpers.ResponseHelper;
import com.bartugsevindik.paymentswitch.payment.controller.PaymentController;
import com.bartugsevindik.paymentswitch.payment.idempotency.dto.IdempotentResult;
import com.bartugsevindik.paymentswitch.payment.operation.dto.PaymentOperationDTO;
import com.bartugsevindik.paymentswitch.payment.operation.dto.RefundRequest;
import com.bartugsevindik.paymentswitch.payment.operation.service.PaymentOperationService;
import com.bartugsevindik.paymentswitch.payment.terminal.security.TerminalPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
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
 * <h1>PaymentOperationController</h1>
 * <p>Onaylı ödemelerin iptali ve iadesi. İstekler ödeme ile aynı şekilde terminal tarafından imzalanır ve
 * Idempotency-Key zorunludur; tekrar gönderilen iade isteği ikinci iade oluşturmaz.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 29.09.2026 - PS-6
 */
@Tag(name = "İptal ve İade İşlemleri", description = "Onaylı ödemelerin iptali (gün sonu öncesi) ve iadesi (kısmi olabilir).")
@RestController
@RequestMapping("/v1/payments/{paymentId}")
@RequiredArgsConstructor
public class PaymentOperationController {
    private final PaymentOperationService paymentOperationService;

    /**
     * <h1>Ödemeyi İptal Et</h1>
     * <p>Gün sonu öncesi iptal. İşlem takasa girmez, kart sahibinin ekstresine yansımaz.</p>
     *
     * @param terminal       İmzası doğrulanmış terminal
     * @param paymentId      Ödeme ID
     * @param idempotencyKey İstek başına tekil key
     * @return İptal işlemi
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 29.09.2026 - PS-6
     */
    @Operation(summary = "Ödemeyi İptal Et (Void)",
               description = "Sadece aynı iş günü, gün sonu saatinden önce ve iade yapılmamış onaylı ödeme iptal edilebilir. Sonuç asenkron gelir; ödeme önce VOIDING, sonra VOIDED olur.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "İptal talebi alındı.",
                    content = @Content(schema = @Schema(implementation = PaymentOperationDTO.class))),
            @ApiResponse(responseCode = "404", description = "Ödeme bulunamadı."),
            @ApiResponse(responseCode = "409", description = "İptal işlemi zaten devam ediyor."),
            @ApiResponse(responseCode = "422", description = "Ödeme iptal edilemez (onaylı değil, iade yapılmış ya da gün sonu geçmiş).")
    })
    @PostMapping("/void")
    public ResponseEntity<ResponseMessage> voidPayment(
            @Parameter(hidden = true) @RequestAttribute(TerminalPrincipal.REQUEST_ATTRIBUTE) TerminalPrincipal terminal,
            @PathVariable String paymentId,
            @Parameter(description = "İstek başına tekil key", required = true)
            @RequestHeader(PaymentController.IDEMPOTENCY_KEY_HEADER)
            @Pattern(regexp = "^[A-Za-z0-9_-]{8,64}$", message = "Idempotency-Key 8-64 karakter olmalı ve sadece harf, rakam, '-' ve '_' içermelidir.")
            String idempotencyKey) {
        return accepted(paymentOperationService.requestVoid(terminal, paymentId, idempotencyKey), "İptal talebi alındı.");
    }

    /**
     * <h1>Ödemeyi İade Et</h1>
     * <p>Ödemenin tamamını ya da bir kısmını iade eder.</p>
     *
     * @param terminal       İmzası doğrulanmış terminal
     * @param paymentId      Ödeme ID
     * @param idempotencyKey İstek başına tekil key
     * @param request        İade tutarı
     * @return İade işlemi
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 29.09.2026 - PS-6
     */
    @Operation(summary = "Ödemeyi İade Et (Refund)",
               description = "Kısmi iade yapılabilir. İade tutarı; başarılı ve sonucu beklenen iadeler düşüldükten sonra kalan tutarı aşamaz. Sonuç asenkron gelir.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "İade talebi alındı.",
                    content = @Content(schema = @Schema(implementation = PaymentOperationDTO.class))),
            @ApiResponse(responseCode = "404", description = "Ödeme bulunamadı."),
            @ApiResponse(responseCode = "422", description = "Ödeme iade edilemez ya da tutar iade edilebilir tutarı aşıyor.")
    })
    @PostMapping("/refunds")
    public ResponseEntity<ResponseMessage> refundPayment(
            @Parameter(hidden = true) @RequestAttribute(TerminalPrincipal.REQUEST_ATTRIBUTE) TerminalPrincipal terminal,
            @PathVariable String paymentId,
            @Parameter(description = "İstek başına tekil key", required = true)
            @RequestHeader(PaymentController.IDEMPOTENCY_KEY_HEADER)
            @Pattern(regexp = "^[A-Za-z0-9_-]{8,64}$", message = "Idempotency-Key 8-64 karakter olmalı ve sadece harf, rakam, '-' ve '_' içermelidir.")
            String idempotencyKey,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "İade tutarı",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = RefundRequest.class),
                            examples = @ExampleObject(value = """
                                    {"amount": 500.00}
                                    """)
                    )
            )
            @Valid @RequestBody RefundRequest request) {
        return accepted(paymentOperationService.requestRefund(terminal, paymentId, idempotencyKey, request), "İade talebi alındı.");
    }

    /**
     * <h1>İptal ve İadeleri Listele</h1>
     *
     * @param terminal  İmzası doğrulanmış terminal
     * @param paymentId Ödeme ID
     * @return İşlemler
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 29.09.2026 - PS-6
     */
    @Operation(summary = "İptal ve İadeleri Listele", description = "Ödeme üzerinde yapılan iptal ve iade işlemlerini durumlarıyla döndürür.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "İşlemler getirildi.",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = PaymentOperationDTO.class)))),
            @ApiResponse(responseCode = "404", description = "Ödeme bulunamadı.")
    })
    @GetMapping("/operations")
    public ResponseEntity<ResponseMessage> getOperations(
            @Parameter(hidden = true) @RequestAttribute(TerminalPrincipal.REQUEST_ATTRIBUTE) TerminalPrincipal terminal,
            @PathVariable String paymentId) {
        return ResponseEntity.ok(ResponseHelper.success("İşlemler getirildi.", paymentOperationService.getOperations(terminal, paymentId)));
    }

    private static ResponseEntity<ResponseMessage> accepted(IdempotentResult<PaymentOperationDTO> result, String message) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .header(PaymentController.IDEMPOTENT_REPLAYED_HEADER, String.valueOf(result.replayed()))
                .body(ResponseHelper.accepted(result.replayed() ? "Talep daha önce alındı." : message, result.body()));
    }
}
