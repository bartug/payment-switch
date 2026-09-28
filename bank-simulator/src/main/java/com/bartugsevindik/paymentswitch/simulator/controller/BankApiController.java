/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.simulator.controller;

import com.bartugsevindik.paymentswitch.common.enums.BankCode;
import com.bartugsevindik.paymentswitch.simulator.dto.BankAuthorizeRequest;
import com.bartugsevindik.paymentswitch.simulator.dto.BankTransactionResponse;
import com.bartugsevindik.paymentswitch.simulator.service.BankSimulatorService;
import com.bartugsevindik.paymentswitch.simulator.service.BankUnavailableException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <h1>BankApiController</h1>
 * <p>Bankaların sanal POS API'sini taklit eder. Dış bir sistem olduğu için cevaplar bizim {@code ResponseMessage}
 * yapımızda değil, bankanın kendi formatındadır; bank-adapter bu formatı kendi modeline çevirir.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-5
 */
@Tag(name = "Banka API", description = "Sahte banka sanal POS uç noktaları")
@RestController
@RequestMapping("/banks/{bankCode}/v1")
@RequiredArgsConstructor
public class BankApiController {
    private final BankSimulatorService bankSimulatorService;

    /**
     * <h1>Satış</h1>
     * <p>Kartı yetkilendirir ve tutarı çeker.</p>
     *
     * @param bankCode Banka
     * @param request  Satış isteği
     * @return İşlem sonucu
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-5
     */
    @Operation(summary = "Satış",
               description = "Tutarın son iki hanesi sonucu belirler: .51 yetersiz bakiye, .05 onaylanmadı, .54 süresi dolmuş kart. Aynı orderId ikinci kez gelirse ilk sonuç döner.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "İşlem sonuçlandı (onay ya da red).",
                    content = @Content(schema = @Schema(implementation = BankTransactionResponse.class))),
            @ApiResponse(responseCode = "503", description = "Banka hizmet veremiyor, işlem yapılmadı.")
    })
    @PostMapping("/authorize")
    public ResponseEntity<BankTransactionResponse> authorize(@PathVariable BankCode bankCode,
                                                             @Valid @RequestBody BankAuthorizeRequest request) {
        return ResponseEntity.ok(bankSimulatorService.authorize(bankCode, request));
    }

    /**
     * <h1>İşlem Sorgula</h1>
     * <p>Sipariş numarası ile işlemin bankadaki durumunu döndürür.</p>
     *
     * @param bankCode Banka
     * @param orderId  Sipariş numarası
     * @return İşlem
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-5
     */
    @Operation(summary = "İşlem Sorgula", description = "Cevabı alınamayan işlemin bankaya ulaşıp ulaşmadığını öğrenmek için.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "İşlem bulundu.",
                    content = @Content(schema = @Schema(implementation = BankTransactionResponse.class))),
            @ApiResponse(responseCode = "404", description = "Banka bu işlemi hiç almadı.")
    })
    @GetMapping("/transactions/{orderId}")
    public ResponseEntity<BankTransactionResponse> inquire(@PathVariable BankCode bankCode, @PathVariable String orderId) {
        return bankSimulatorService.inquire(bankCode, orderId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * <h1>Teknik İptal</h1>
     * <p>İşlemi geri alır. İşlem bankaya henüz ulaşmadıysa sipariş numarasını iptal olarak işaretler.</p>
     *
     * @param bankCode Banka
     * @param orderId  Sipariş numarası
     * @return İptal sonucu
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-5
     */
    @Operation(summary = "Teknik İptal (Reversal)", description = "İdempotent; aynı sipariş için tekrar çağrılabilir.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "İşlem iptal edildi.",
                    content = @Content(schema = @Schema(implementation = BankTransactionResponse.class)))
    })
    @PostMapping("/transactions/{orderId}/reversal")
    public ResponseEntity<BankTransactionResponse> reverse(@PathVariable BankCode bankCode, @PathVariable String orderId) {
        return ResponseEntity.ok(bankSimulatorService.reverse(bankCode, orderId));
    }

    /**
     * <h1>Echo</h1>
     * <p>Bankanın ayakta olup olmadığını kontrol eder (ISO 8583 0800).</p>
     *
     * @param bankCode Banka
     * @return Boş cevap
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-5
     */
    @Operation(summary = "Echo", description = "ISO 8583 0800 ağ yönetimi mesajı karşılığı.")
    @GetMapping("/echo")
    public ResponseEntity<Void> echo(@PathVariable BankCode bankCode) {
        bankSimulatorService.echo(bankCode);
        return ResponseEntity.ok().build();
    }

    /**
     * Controller içindeki handler global handler'dan önce çalışır; banka formatında 503 döner.
     */
    @ExceptionHandler(BankUnavailableException.class)
    public ResponseEntity<BankTransactionResponse> handleUnavailable(BankUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new BankTransactionResponse(null, null, "91", null, null, ex.getMessage()));
    }
}
