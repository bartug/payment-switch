/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.routing.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * <h1>RoutingReason</h1>
 * <p>Routing kararının sebebi. Karar kaydında, event'te ve üye işyerine dönen hata mesajında kullanılır.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-4
 */
@Getter
@RequiredArgsConstructor
public enum RoutingReason {
    ON_US_INSTALLMENT(RoutingOutcome.ROUTED, "Taksitli işlem kartın program bankasına yönlendirildi."),
    LOWEST_COST(RoutingOutcome.ROUTED, "Tek çekim işlem en düşük komisyonlu aktif bankaya yönlendirildi."),
    UNKNOWN_BIN_INSTALLMENT(RoutingOutcome.REJECTED, "Kart BIN'i tanımsız, taksitli işlem yapılamaz."),
    NON_CREDIT_INSTALLMENT(RoutingOutcome.REJECTED, "Banka kartı ve ön ödemeli kartlarla taksitli işlem yapılamaz."),
    NO_INSTALLMENT_PROGRAM(RoutingOutcome.REJECTED, "Kart bir taksit programına dahil değil."),
    PROGRAM_BANK_UNAVAILABLE(RoutingOutcome.REJECTED, "Kartın program bankası şu an işlem almıyor, taksitli işlem yapılamaz."),
    NO_ACTIVE_BANK(RoutingOutcome.REJECTED, "İşlem alabilecek aktif banka yok.");

    private final RoutingOutcome outcome;
    private final String description;
}
