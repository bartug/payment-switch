/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.routing.rule;

import com.bartugsevindik.paymentswitch.common.enums.BankCode;
import com.bartugsevindik.paymentswitch.routing.enums.RoutingOutcome;
import com.bartugsevindik.paymentswitch.routing.enums.RoutingReason;

/**
 * <h1>RoutingResult</h1>
 * <p>Bir kuralın verdiği karar: ya bir bankaya yönlendir ya reddet.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-4
 */
public record RoutingResult(RoutingReason reason, BankCode bankCode, boolean onUs) {

    public static RoutingResult route(RoutingReason reason, BankCode bankCode, boolean onUs) {
        return new RoutingResult(reason, bankCode, onUs);
    }

    public static RoutingResult reject(RoutingReason reason) {
        return new RoutingResult(reason, null, false);
    }

    public RoutingOutcome outcome() {
        return reason.getOutcome();
    }

    public boolean isRouted() {
        return outcome() == RoutingOutcome.ROUTED;
    }
}
