/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.routing.dto;

import com.bartugsevindik.paymentswitch.common.enums.BankCode;
import com.bartugsevindik.paymentswitch.routing.enums.RoutingOutcome;
import com.bartugsevindik.paymentswitch.routing.enums.RoutingReason;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "RoutingResultDTO", description = "Routing kararı ve karara giden bilgiler")
public class RoutingResultDTO {

    @Schema(description = "Sonuç", example = "ROUTED")
    private RoutingOutcome outcome;

    @Schema(description = "Yönlendirilen banka", example = "YKB")
    private BankCode bankCode;

    @Schema(description = "Kart ile POS aynı bankanın mı", example = "true")
    private Boolean onUs;

    @Schema(description = "Karar sebebi", example = "ON_US_INSTALLMENT")
    private RoutingReason reason;

    @Schema(description = "Karar açıklaması", example = "Taksitli işlem kartın program bankasına yönlendirildi.")
    private String description;

    @Schema(description = "BIN çözümleme sonucu. Tanımsız BIN'de boş.")
    private BinInfo binInfo;

    @Schema(description = "Karar anında aktif olan bankalar")
    private List<AcquirerBankInfo> activeBanks;
}
