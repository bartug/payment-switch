/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.bank.controller;

import com.bartugsevindik.paymentswitch.bank.config.BankAdapterProperties;
import com.bartugsevindik.paymentswitch.bank.dto.BankTransactionDTO;
import com.bartugsevindik.paymentswitch.bank.dto.CircuitStatusDTO;
import com.bartugsevindik.paymentswitch.bank.entity.BankTransaction;
import com.bartugsevindik.paymentswitch.bank.repository.BankTransactionRepository;
import com.bartugsevindik.paymentswitch.common.dto.ResponseMessage;
import com.bartugsevindik.paymentswitch.common.exception.NotFoundException;
import com.bartugsevindik.paymentswitch.common.helpers.ResponseHelper;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <h1>BankAdapterAdminController</h1>
 * <p>Banka işlemlerinin ve circuit breaker'ların durumunu gösterir.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-5
 */
@Tag(name = "Bank Adapter Yönetimi", description = "Banka işlemleri ve circuit breaker durumları izlenir.")
@RestController
@RequestMapping("/v1/admin")
@RequiredArgsConstructor
public class BankAdapterAdminController {
    private final BankTransactionRepository bankTransactionRepository;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final BulkheadRegistry bulkheadRegistry;
    private final BankAdapterProperties properties;

    /**
     * <h1>Banka İşlemini Getir</h1>
     * <p>Ödemenin bankadaki durumunu, inquiry/reversal denemelerini ve son hatayı döndürür.</p>
     *
     * @param paymentId Ödeme ID
     * @return Banka işlemi
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-5
     */
    @Operation(summary = "Banka İşlemini Getir", description = "UNKNOWN / REVERSING / MANUAL_REVIEW işlemleri incelemek için.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "İşlem bulundu.",
                    content = @Content(schema = @Schema(implementation = BankTransactionDTO.class))),
            @ApiResponse(responseCode = "404", description = "İşlem bulunamadı.")
    })
    @GetMapping("/transactions/{paymentId}")
    public ResponseEntity<ResponseMessage> getTransaction(@PathVariable String paymentId) {
        BankTransaction transaction = bankTransactionRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new NotFoundException("Banka işlemi", "paymentId", paymentId));
        return ResponseEntity.ok(ResponseHelper.success("Banka işlemi getirildi.", toDto(transaction)));
    }

    /**
     * <h1>Circuit Breaker Durumları</h1>
     * <p>Her bankanın circuit breaker ve bulkhead durumunu döndürür.</p>
     *
     * @return Banka bazında durum
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-5
     */
    @Operation(summary = "Circuit Breaker Durumları", description = "OPEN: bankaya istek gönderilmiyor. HALF_OPEN: echo ile deneniyor.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Durumlar getirildi.",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = CircuitStatusDTO.class))))
    })
    @GetMapping("/circuits")
    public ResponseEntity<ResponseMessage> getCircuits() {
        List<CircuitStatusDTO> circuits = properties.getBanks().stream().map(bank -> {
            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(bank.name());
            return new CircuitStatusDTO(bank, cb.getState().name(), cb.getMetrics().getFailureRate(),
                    cb.getMetrics().getNumberOfBufferedCalls(),
                    bulkheadRegistry.bulkhead(bank.name()).getMetrics().getAvailableConcurrentCalls());
        }).toList();
        return ResponseEntity.ok(ResponseHelper.success("Circuit durumları getirildi.", circuits));
    }

    private static BankTransactionDTO toDto(BankTransaction t) {
        return new BankTransactionDTO(t.getPaymentId(), t.getOrderId(), t.getBankCode(), t.getStatus(), t.getResponseCode(),
                t.getAuthCode(), t.getRrn(), t.getMessage(), t.getAttempts(), t.getLastError(), t.getCreatedDate());
    }
}
