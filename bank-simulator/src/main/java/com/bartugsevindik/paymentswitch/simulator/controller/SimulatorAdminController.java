/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.simulator.controller;

import com.bartugsevindik.paymentswitch.common.dto.ResponseMessage;
import com.bartugsevindik.paymentswitch.common.enums.BankCode;
import com.bartugsevindik.paymentswitch.common.helpers.ResponseHelper;
import com.bartugsevindik.paymentswitch.simulator.dto.ChaosSettings;
import com.bartugsevindik.paymentswitch.simulator.service.BankSimulatorService;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * <h1>SimulatorAdminController</h1>
 * <p>Bankaların hata davranışını (chaos) yönetir.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-5
 */
@Tag(name = "Simülatör Yönetimi", description = "Banka hata senaryoları yönetilir.")
@RestController
@RequestMapping("/v1/admin/banks")
@RequiredArgsConstructor
public class SimulatorAdminController {
    private final BankSimulatorService bankSimulatorService;

    /**
     * <h1>Chaos Ayarlarını Listele</h1>
     *
     * @return Banka bazında chaos ayarları
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-5
     */
    @Operation(summary = "Chaos Ayarlarını Listele", description = "Tüm bankaların hata davranışını döndürür.")
    @GetMapping("/chaos")
    public ResponseEntity<ResponseMessage> getAllChaos() {
        return ResponseEntity.ok(ResponseHelper.success("Chaos ayarları getirildi.", bankSimulatorService.getAllChaos()));
    }

    /**
     * <h1>Banka Kaydının Tutarını Değiştir</h1>
     * <p>Mutabakatta tutar farkı senaryosunu denemek için.</p>
     *
     * @param bankCode Banka
     * @param orderId  Sipariş numarası
     * @param amount   Yeni tutar (kuruş)
     * @return Boş cevap
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 29.09.2026 - PS-7
     */
    @Operation(summary = "Banka Kaydının Tutarını Değiştir", description = "Sadece test: mutabakatta AMOUNT_MISMATCH oluşturur.")
    @PutMapping("/{bankCode}/transactions/{orderId}/amount")
    public ResponseEntity<ResponseMessage> tamperAmount(@PathVariable BankCode bankCode, @PathVariable String orderId,
                                                        @RequestParam long amount) {
        bankSimulatorService.tamperAmount(bankCode, orderId, amount);
        return ResponseEntity.ok(ResponseHelper.success("Banka kaydı değiştirildi.", null));
    }

    /**
     * <h1>Chaos Ayarla</h1>
     * <p>Bankanın hata davranışını değiştirir.</p>
     *
     * @param bankCode Banka
     * @param settings Chaos ayarları
     * @return Güncel ayarlar
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-5
     */
    @Operation(summary = "Chaos Ayarla",
               description = "lateResponseRate: işlem yapılır ama cevap geç döner (adapter timeout'a düşer, inquiry ile netleşir). down: banka tamamen kapalı (circuit breaker açılır, routing bankayı devre dışı bırakır).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ayarlar güncellendi.",
                    content = @Content(schema = @Schema(implementation = ChaosSettings.class)))
    })
    @PutMapping("/{bankCode}/chaos")
    public ResponseEntity<ResponseMessage> updateChaos(
            @PathVariable BankCode bankCode,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Chaos ayarları",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ChaosSettings.class),
                            examples = {
                                    @ExampleObject(name = "Cevap gecikiyor", value = """
                                            {"down": false, "latencyMs": 0, "failureRate": 0, "lateResponseRate": 1, "lateResponseMs": 15000}
                                            """),
                                    @ExampleObject(name = "Banka kapalı", value = """
                                            {"down": true, "latencyMs": 0, "failureRate": 0, "lateResponseRate": 0, "lateResponseMs": 0}
                                            """),
                                    @ExampleObject(name = "Normal", value = """
                                            {"down": false, "latencyMs": 0, "failureRate": 0, "lateResponseRate": 0, "lateResponseMs": 0}
                                            """)
                            }
                    )
            )
            @Valid @RequestBody ChaosSettings settings) {
        bankSimulatorService.updateChaos(bankCode, settings);
        return ResponseEntity.ok(ResponseHelper.success("Chaos ayarları güncellendi.", settings));
    }
}
