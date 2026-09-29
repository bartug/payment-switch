/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.reconciliation.controller;

import com.bartugsevindik.paymentswitch.common.dto.ResponseMessage;
import com.bartugsevindik.paymentswitch.common.helpers.ResponseHelper;
import com.bartugsevindik.paymentswitch.payment.reconciliation.dto.ReconciliationItemDTO;
import com.bartugsevindik.paymentswitch.payment.reconciliation.dto.ReconciliationRequest;
import com.bartugsevindik.paymentswitch.payment.reconciliation.dto.ReconciliationRunDTO;
import com.bartugsevindik.paymentswitch.payment.reconciliation.service.ReconciliationService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <h1>ReconciliationController</h1>
 * <p>Banka gün sonu dosyası ile mutabakat. Finans ve operasyon ekibi içindir.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 29.09.2026 - PS-7
 */
@Tag(name = "Mutabakat", description = "Bizim kayıtlarımız ile bankanın gün sonu dosyasının karşılaştırılması.")
@RestController
@RequestMapping("/v1/admin/reconciliations")
@RequiredArgsConstructor
public class ReconciliationController {
    private final ReconciliationService reconciliationService;

    /**
     * <h1>Mutabakat Çalıştır</h1>
     *
     * @param request Banka ve iş günü
     * @return Özet ve aksiyon gerektiren satırlar
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 29.09.2026 - PS-7
     */
    @Operation(summary = "Mutabakat Çalıştır",
               description = "Normalde her gece 03:00'te bir önceki gün için otomatik çalışır. Aynı gün için tekrar çalıştırılabilir; her çalışma ayrı kaydedilir.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Mutabakat tamamlandı.",
                    content = @Content(schema = @Schema(implementation = ReconciliationRunDTO.class)))
    })
    @PostMapping
    public ResponseEntity<ResponseMessage> reconcile(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Banka ve iş günü",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ReconciliationRequest.class),
                            examples = @ExampleObject(value = """
                                    {"bankCode": "YKB", "businessDate": "2026-09-29"}
                                    """)
                    )
            )
            @Valid @RequestBody ReconciliationRequest request) {
        ReconciliationRunDTO run = reconciliationService.reconcile(request.getBankCode(), request.getBusinessDate());
        return ResponseEntity.ok(ResponseHelper.success("Mutabakat tamamlandı.", run));
    }

    /**
     * <h1>Mutabakat Satırlarını Getir</h1>
     *
     * @param runId Çalışma ID
     * @return Tüm satırlar (eşleşenler dahil)
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 29.09.2026 - PS-7
     */
    @Operation(summary = "Mutabakat Satırlarını Getir", description = "Eşleşenler dahil tüm satırları döndürür.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Satırlar getirildi.",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ReconciliationItemDTO.class)))),
            @ApiResponse(responseCode = "404", description = "Mutabakat bulunamadı.")
    })
    @GetMapping("/{runId}/items")
    public ResponseEntity<ResponseMessage> getItems(@PathVariable String runId) {
        return ResponseEntity.ok(ResponseHelper.success("Satırlar getirildi.", reconciliationService.getItems(runId)));
    }
}
