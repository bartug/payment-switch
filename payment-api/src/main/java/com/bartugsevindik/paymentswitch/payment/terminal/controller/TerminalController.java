/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.terminal.controller;

import com.bartugsevindik.paymentswitch.common.dto.ResponseMessage;
import com.bartugsevindik.paymentswitch.common.helpers.ResponseHelper;
import com.bartugsevindik.paymentswitch.payment.terminal.dto.TerminalCreateRequest;
import com.bartugsevindik.paymentswitch.payment.terminal.dto.TerminalDTO;
import com.bartugsevindik.paymentswitch.payment.terminal.enums.TerminalStatus;
import com.bartugsevindik.paymentswitch.payment.terminal.service.TerminalService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <h1>TerminalController</h1>
 * <p>Terminal tanımlama ve durum yönetimi uç noktalarını barındırır.</p>
 * <p>Bu uç noktalar terminaller için değil, operasyon ekibi içindir. Backoffice kimlik doğrulaması ile korunacak.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-2
 */
@Tag(name = "Terminal İşlemleri", description = "Ödeme gönderebilen terminaller yönetilir.")
@RestController
@RequestMapping("/v1/admin/terminals")
@RequiredArgsConstructor
public class TerminalController {
    private final TerminalService terminalService;

    /**
     * <h1>Terminal Oluştur</h1>
     * <p>Terminali tanımlar ve imza secret'ını bir kez döndürür.</p>
     *
     * @param request Terminal bilgileri
     * @return Secret'ı içeren terminal bilgisi
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-2
     */
    @Operation(summary = "Terminal Oluştur",
               description = "Terminali tanımlar. Dönen secret istek imzalamada kullanılır ve bir daha görüntülenemez; kaybedilirse terminal yeniden tanımlanmalıdır.")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Terminal oluşturuldu.",
                    content = @Content(schema = @Schema(implementation = TerminalDTO.class))
            ),
            @ApiResponse(responseCode = "409", description = "Terminal zaten tanımlı.")
    })
    @PostMapping
    public ResponseEntity<ResponseMessage> createTerminal(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Terminal bilgileri",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = TerminalCreateRequest.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "terminalId": "TRM00000001",
                                      "merchantId": "MRC0000001",
                                      "terminalType": "VIRTUAL"
                                    }
                                    """)
                    )
            )
            @Valid @RequestBody TerminalCreateRequest request) {
        TerminalDTO dto = terminalService.createTerminal(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ResponseHelper.created("Terminal oluşturuldu. Secret'ı güvenli bir yerde saklayın.", dto));
    }

    /**
     * <h1>Terminali Pasife Al</h1>
     * <p>Terminalin işlem göndermesini engeller.</p>
     *
     * @param terminalId Terminal numarası
     * @return Güncel terminal bilgisi
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-2
     */
    @Operation(summary = "Terminali Pasife Al",
               description = "Kapatılan ya da çalıntı bildirilen terminalin işlem göndermesini engeller.")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Terminal pasife alındı.",
                    content = @Content(schema = @Schema(implementation = TerminalDTO.class))
            ),
            @ApiResponse(responseCode = "404", description = "Terminal bulunamadı.")
    })
    @PutMapping("/{terminalId}/passive")
    public ResponseEntity<ResponseMessage> deactivateTerminal(@PathVariable String terminalId) {
        TerminalDTO dto = terminalService.updateTerminalStatus(terminalId, TerminalStatus.PASSIVE);
        return ResponseEntity.ok(ResponseHelper.success("Terminal pasife alındı.", dto));
    }

    /**
     * <h1>Terminali Aktife Al</h1>
     * <p>Pasife alınmış terminalin tekrar işlem göndermesine izin verir.</p>
     *
     * @param terminalId Terminal numarası
     * @return Güncel terminal bilgisi
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-2
     */
    @Operation(summary = "Terminali Aktife Al",
               description = "Pasife alınmış terminalin tekrar işlem göndermesine izin verir.")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Terminal aktife alındı.",
                    content = @Content(schema = @Schema(implementation = TerminalDTO.class))
            ),
            @ApiResponse(responseCode = "404", description = "Terminal bulunamadı.")
    })
    @PutMapping("/{terminalId}/active")
    public ResponseEntity<ResponseMessage> activateTerminal(@PathVariable String terminalId) {
        TerminalDTO dto = terminalService.updateTerminalStatus(terminalId, TerminalStatus.ACTIVE);
        return ResponseEntity.ok(ResponseHelper.success("Terminal aktife alındı.", dto));
    }
}
