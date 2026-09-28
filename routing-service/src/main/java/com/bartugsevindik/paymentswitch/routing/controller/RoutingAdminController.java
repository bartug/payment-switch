/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.routing.controller;

import com.bartugsevindik.paymentswitch.common.dto.ResponseMessage;
import com.bartugsevindik.paymentswitch.common.enums.BankCode;
import com.bartugsevindik.paymentswitch.common.exception.NotFoundException;
import com.bartugsevindik.paymentswitch.common.helpers.ResponseHelper;
import com.bartugsevindik.paymentswitch.routing.dto.AcquirerBankInfo;
import com.bartugsevindik.paymentswitch.routing.dto.BinInfo;
import com.bartugsevindik.paymentswitch.routing.dto.RoutingResultDTO;
import com.bartugsevindik.paymentswitch.routing.dto.RoutingSimulationRequest;
import com.bartugsevindik.paymentswitch.routing.service.AcquirerBankService;
import com.bartugsevindik.paymentswitch.routing.service.BinLookupService;
import com.bartugsevindik.paymentswitch.routing.service.RoutingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <h1>RoutingAdminController</h1>
 * <p>Routing kararlarını denemek, BIN sorgulamak ve bankaları aktif/pasif yapmak için operasyon uç noktaları.</p>
 * <p>Backoffice kimlik doğrulaması ile korunacak.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-4
 */
@Tag(name = "Routing Yönetimi", description = "Routing kararları, BIN tablosu ve banka durumları yönetilir.")
@RestController
@RequestMapping("/v1/admin")
@RequiredArgsConstructor
public class RoutingAdminController {
    private final RoutingService routingService;
    private final BinLookupService binLookupService;
    private final AcquirerBankService acquirerBankService;

    /**
     * <h1>Routing Kararını Dene</h1>
     * <p>Ödeme oluşturmadan, verilen BIN ve taksit sayısı için routing kararını döndürür.</p>
     *
     * @param request BIN ve taksit sayısı
     * @return Karar ve karara giden bilgiler
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-4
     */
    @Operation(summary = "Routing Kararını Dene",
               description = "Kaydetmeden ve event üretmeden routing kararını döndürür. Bir bankayı pasife alıp tekrar denemek failover'ı gösterir.")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Karar verildi.",
                    content = @Content(schema = @Schema(implementation = RoutingResultDTO.class))
            )
    })
    @PostMapping("/routing/simulate")
    public ResponseEntity<ResponseMessage> simulate(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "BIN ve taksit sayısı",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = RoutingSimulationRequest.class),
                            examples = {
                                    @ExampleObject(name = "World kart, 3 taksit", value = """
                                            {"cardBin": "54006170", "installmentCount": 3}
                                            """),
                                    @ExampleObject(name = "Banka kartı, taksit", value = """
                                            {"cardBin": "97920012", "installmentCount": 6}
                                            """),
                                    @ExampleObject(name = "Tanımsız BIN, tek çekim", value = """
                                            {"cardBin": "60111111", "installmentCount": 1}
                                            """)
                            }
                    )
            )
            @Valid @RequestBody RoutingSimulationRequest request) {
        return ResponseEntity.ok(ResponseHelper.success("Routing kararı verildi.", routingService.simulate(request)));
    }

    /**
     * <h1>BIN Sorgula</h1>
     * <p>BIN'in hangi bankaya ve taksit programına ait olduğunu döndürür.</p>
     *
     * @param bin Kartın ilk 6 ya da 8 hanesi
     * @return BIN bilgisi
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-4
     */
    @Operation(summary = "BIN Sorgula", description = "En uzun prefix eşleşmesi ile BIN bilgisini döndürür (önce 8, sonra 6 hane).")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "BIN bulundu.",
                    content = @Content(schema = @Schema(implementation = BinInfo.class))
            ),
            @ApiResponse(responseCode = "404", description = "BIN tanımsız.")
    })
    @GetMapping("/bins/{bin}")
    public ResponseEntity<ResponseMessage> getBin(@PathVariable String bin) {
        BinInfo info = binLookupService.lookup(bin)
                .orElseThrow(() -> new NotFoundException("BIN", "bin", bin));
        return ResponseEntity.ok(ResponseHelper.success("BIN bilgisi getirildi.", info));
    }

    /**
     * <h1>Bankaları Listele</h1>
     * <p>İşlem gönderilebilen bankaları, durumlarını ve komisyon oranlarını döndürür.</p>
     *
     * @return Banka listesi
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-4
     */
    @Operation(summary = "Bankaları Listele", description = "Bankaları, aktif/pasif durumlarını ve komisyon oranlarını (baz puan) döndürür.")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Bankalar getirildi.",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = AcquirerBankInfo.class)))
            )
    })
    @GetMapping("/banks")
    public ResponseEntity<ResponseMessage> getBanks() {
        List<AcquirerBankInfo> banks = acquirerBankService.getAllBanks();
        return ResponseEntity.ok(ResponseHelper.success("Bankalar getirildi.", banks));
    }

    /**
     * <h1>Bankayı Pasife Al</h1>
     * <p>Bankaya yeni işlem gönderilmesini durdurur.</p>
     *
     * @param bankCode Banka kodu
     * @return Güncel banka bilgisi
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-4
     */
    @Operation(summary = "Bankayı Pasife Al",
               description = "Tek çekim işlemler bir sonraki en ucuz bankaya kayar (failover). Bu bankanın taksit programına ait kartlarla taksitli işlem reddedilir.")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Banka pasife alındı.",
                    content = @Content(schema = @Schema(implementation = AcquirerBankInfo.class))
            ),
            @ApiResponse(responseCode = "404", description = "Banka bulunamadı.")
    })
    @PutMapping("/banks/{bankCode}/passive")
    public ResponseEntity<ResponseMessage> deactivateBank(@PathVariable BankCode bankCode) {
        return ResponseEntity.ok(ResponseHelper.success("Banka pasife alındı.", acquirerBankService.updateBankStatus(bankCode, false)));
    }

    /**
     * <h1>Bankayı Aktife Al</h1>
     * <p>Bankaya tekrar işlem gönderilmesini sağlar.</p>
     *
     * @param bankCode Banka kodu
     * @return Güncel banka bilgisi
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-4
     */
    @Operation(summary = "Bankayı Aktife Al", description = "Bankaya tekrar işlem gönderilmesini sağlar.")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Banka aktife alındı.",
                    content = @Content(schema = @Schema(implementation = AcquirerBankInfo.class))
            ),
            @ApiResponse(responseCode = "404", description = "Banka bulunamadı.")
    })
    @PutMapping("/banks/{bankCode}/active")
    public ResponseEntity<ResponseMessage> activateBank(@PathVariable BankCode bankCode) {
        return ResponseEntity.ok(ResponseHelper.success("Banka aktife alındı.", acquirerBankService.updateBankStatus(bankCode, true)));
    }
}
