/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.routing.rule;

import com.bartugsevindik.paymentswitch.routing.enums.RoutingReason;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * <h1>UnknownBinInstallmentRule</h1>
 * <p>BIN tanımsızsa kartın taksit programı bilinemez; taksitli işlem reddedilir. Tek çekim işlem devam eder,
 * maliyet kuralı tüm bankaları off-us kabul ederek karar verir.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-4
 */
@Component
@Order(10)
public class UnknownBinInstallmentRule implements RoutingRule {

    @Override
    public Optional<RoutingResult> evaluate(RoutingContext context) {
        if (context.isInstallment() && context.binInfo().isEmpty()) {
            return Optional.of(RoutingResult.reject(RoutingReason.UNKNOWN_BIN_INSTALLMENT));
        }
        return Optional.empty();
    }
}
