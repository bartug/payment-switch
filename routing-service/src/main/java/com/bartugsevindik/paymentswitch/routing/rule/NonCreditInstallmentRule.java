/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.routing.rule;

import com.bartugsevindik.paymentswitch.routing.enums.CardType;
import com.bartugsevindik.paymentswitch.routing.enums.RoutingReason;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * <h1>NonCreditInstallmentRule</h1>
 * <p>Taksit bir kredi ürünüdür; banka kartı ve ön ödemeli kartla taksitli işlem yapılamaz.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-4
 */
@Component
@Order(20)
public class NonCreditInstallmentRule implements RoutingRule {

    @Override
    public Optional<RoutingResult> evaluate(RoutingContext context) {
        boolean nonCredit = context.binInfo().map(bin -> bin.cardType() != CardType.CREDIT).orElse(false);
        if (context.isInstallment() && nonCredit) {
            return Optional.of(RoutingResult.reject(RoutingReason.NON_CREDIT_INSTALLMENT));
        }
        return Optional.empty();
    }
}
