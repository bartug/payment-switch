/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.ledger.controller;

import com.bartugsevindik.paymentswitch.common.dto.ResponseMessage;
import com.bartugsevindik.paymentswitch.common.helpers.ResponseHelper;
import com.bartugsevindik.paymentswitch.payment.ledger.dto.AccountBalanceDTO;
import com.bartugsevindik.paymentswitch.payment.ledger.dto.JournalEntryDTO;
import com.bartugsevindik.paymentswitch.payment.ledger.dto.TrialBalanceDTO;
import com.bartugsevindik.paymentswitch.payment.ledger.service.LedgerService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * <h1>LedgerAdminController</h1>
 * <p>Muhasebe kayıtları, hesap bakiyeleri ve mizan. Finans ve operasyon ekibi içindir.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 29.09.2026 - PS-7
 */
@Tag(name = "Ledger", description = "Çift taraflı muhasebe kayıtları, bakiyeler ve mizan.")
@RestController
@RequestMapping("/v1/admin/ledger")
@RequiredArgsConstructor
public class LedgerAdminController {
    private final LedgerService ledgerService;

    /**
     * <h1>Ödemenin Kayıtlarını Getir</h1>
     *
     * @param paymentId Ödeme ID
     * @return Yevmiye kayıtları
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 29.09.2026 - PS-7
     */
    @Operation(summary = "Ödemenin Kayıtlarını Getir", description = "Satış, iade ve iptal kayıtlarını satırlarıyla döndürür.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Kayıtlar getirildi.",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = JournalEntryDTO.class))))
    })
    @GetMapping("/payments/{paymentId}")
    public ResponseEntity<ResponseMessage> getEntries(@PathVariable String paymentId) {
        return ResponseEntity.ok(ResponseHelper.success("Kayıtlar getirildi.", ledgerService.getEntries(paymentId)));
    }

    /**
     * <h1>Hesap Bakiyelerini Getir</h1>
     *
     * @param account Hesap kodu ön eki. Örn. {@code MERCHANT_PAYABLE:MRC0000001}, {@code BANK_RECEIVABLE}
     * @return Hesap bakiyeleri
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 29.09.2026 - PS-7
     */
    @Operation(summary = "Hesap Bakiyelerini Getir",
               description = "MERCHANT_PAYABLE: üye işyerine ödenecek tutar. BANK_RECEIVABLE: bankadan gelecek tutar. FEE_REVENUE: komisyon geliri.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Bakiyeler getirildi.",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = AccountBalanceDTO.class))))
    })
    @GetMapping("/balances")
    public ResponseEntity<ResponseMessage> getBalances(@RequestParam(required = false, defaultValue = "") String account) {
        return ResponseEntity.ok(ResponseHelper.success("Bakiyeler getirildi.", ledgerService.getBalances(account)));
    }

    /**
     * <h1>Mizan</h1>
     *
     * @return Toplam borç ve alacak
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 29.09.2026 - PS-7
     */
    @Operation(summary = "Mizan", description = "Tüm kayıtların borç ve alacak toplamı. balanced=false ise sistemde para oluşmuş ya da kaybolmuştur.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Mizan getirildi.",
                    content = @Content(schema = @Schema(implementation = TrialBalanceDTO.class)))
    })
    @GetMapping("/trial-balance")
    public ResponseEntity<ResponseMessage> getTrialBalance() {
        return ResponseEntity.ok(ResponseHelper.success("Mizan getirildi.", ledgerService.getTrialBalance()));
    }
}
