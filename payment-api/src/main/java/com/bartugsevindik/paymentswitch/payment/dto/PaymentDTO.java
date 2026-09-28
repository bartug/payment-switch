/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.dto;

import com.bartugsevindik.paymentswitch.common.enums.BankCode;
import com.bartugsevindik.paymentswitch.common.enums.TerminalType;
import com.bartugsevindik.paymentswitch.payment.enums.PaymentStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "PaymentDTO", description = "Ödeme bilgisi")
public class PaymentDTO {

    @Schema(description = "Ödeme ID", example = "8f14e45f-ceea-4e7a-9d1c-5b6a3f0e2c11")
    private String paymentId;

    @Schema(description = "Üye işyeri numarası", example = "MRC0000001")
    private String merchantId;

    @Schema(description = "Terminal numarası", example = "TRM00000001")
    private String terminalId;

    @Schema(description = "Terminal tipi", example = "VIRTUAL")
    private TerminalType terminalType;

    @Schema(description = "İşlem tutarı", example = "1250.50")
    private BigDecimal amount;

    @Schema(description = "Para birimi", example = "TRY")
    private String currency;

    @Schema(description = "Taksit sayısı", example = "3")
    private Integer installmentCount;

    @Schema(description = "Maskeli kart numarası", example = "540061******2306")
    private String maskedCardNumber;

    @Schema(description = "Ödeme durumu", example = "PENDING")
    private PaymentStatus paymentStatus;

    @Schema(description = "İşlemin yönlendirildiği banka", example = "YKB")
    private BankCode bankCode;

    @Schema(description = "Banka onay kodu", example = "123456")
    private String authCode;

    @Schema(description = "Banka cevap kodu", example = "00")
    private String responseCode;

    @Schema(description = "Ödeme bankaya gönderilemediyse sebebi", example = "Banka kartı ve ön ödemeli kartlarla taksitli işlem yapılamaz.")
    private String failureReason;

    @Schema(description = "Oluşturulma tarihi")
    private LocalDateTime createdDate;
}
