/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.routing.rule;

import com.bartugsevindik.paymentswitch.common.enums.BankCode;
import com.bartugsevindik.paymentswitch.routing.dto.BinInfo;
import com.bartugsevindik.paymentswitch.routing.enums.RoutingReason;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * <h1>InstallmentProgramRule</h1>
 * <p>Taksitli işlem kartın taksit programının bankasına gider (World → YKB, Bonus → Garanti...).
 * Taksit kampanyası ve taksit geri ödemesi o bankada tanımlı olduğu için başka bankaya yönlendirilemez.</p>
 * <p>Program bankası kapalıysa <b>failover yapılmaz</b>, işlem reddedilir. Kart sahibi tek çekim deneyebilir.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-4
 */
@Component
@Order(30)
public class InstallmentProgramRule implements RoutingRule {

    @Override
    public Optional<RoutingResult> evaluate(RoutingContext context) {
        if (!context.isInstallment()) {
            return Optional.empty();
        }

        BinInfo bin = context.binInfo().orElseThrow();
        if (bin.cardProgram() == null) {
            return Optional.of(RoutingResult.reject(RoutingReason.NO_INSTALLMENT_PROGRAM));
        }

        BankCode programBank = bin.cardProgram().getProgramBank();
        boolean available = context.activeBanks().stream().anyMatch(bank -> bank.bankCode() == programBank);
        if (!available) {
            return Optional.of(RoutingResult.reject(RoutingReason.PROGRAM_BANK_UNAVAILABLE));
        }
        return Optional.of(RoutingResult.route(RoutingReason.ON_US_INSTALLMENT, programBank, programBank == bin.issuerBank()));
    }
}
