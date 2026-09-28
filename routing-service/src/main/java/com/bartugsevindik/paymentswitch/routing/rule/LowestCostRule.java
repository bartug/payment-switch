/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.routing.rule;

import com.bartugsevindik.paymentswitch.common.enums.BankCode;
import com.bartugsevindik.paymentswitch.routing.dto.AcquirerBankInfo;
import com.bartugsevindik.paymentswitch.routing.dto.BinInfo;
import com.bartugsevindik.paymentswitch.routing.enums.RoutingReason;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.Optional;

/**
 * <h1>LowestCostRule</h1>
 * <p>Tek çekim işlem, aktif bankalar arasında komisyonu en düşük olana gider. Kartın kendi bankası on-us oranı
 * uyguladığı için genelde o kazanır; kapalıysa bir sonraki en ucuz bankaya geçilir (failover).</p>
 * <p>Eşit komisyonda on-us banka, o da eşitse sabit bir sıra (enum sırası) seçilir; aynı girdi her zaman aynı bankaya gider.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-4
 */
@Component
@Order(40)
public class LowestCostRule implements RoutingRule {

    @Override
    public Optional<RoutingResult> evaluate(RoutingContext context) {
        BankCode issuer = context.binInfo().map(BinInfo::issuerBank).orElse(null);

        return Optional.of(context.activeBanks().stream()
                .min(Comparator.<AcquirerBankInfo>comparingInt(bank -> bank.rateFor(issuer))
                        .thenComparing(bank -> bank.bankCode() != issuer)
                        .thenComparing(AcquirerBankInfo::bankCode))
                .map(bank -> RoutingResult.route(RoutingReason.LOWEST_COST, bank.bankCode(), bank.bankCode() == issuer))
                .orElseGet(() -> RoutingResult.reject(RoutingReason.NO_ACTIVE_BANK)));
    }
}
