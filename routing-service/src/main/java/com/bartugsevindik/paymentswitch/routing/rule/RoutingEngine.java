/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.routing.rule;

import com.bartugsevindik.paymentswitch.routing.enums.RoutingReason;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * <h1>RoutingEngine</h1>
 * <p>Kuralları sırayla çalıştırır, karar veren ilk kuralın sonucunu döndürür. Spring {@code List<RoutingRule>}'u
 * {@code @Order} sırasıyla enjekte eder.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-4
 */
@Component
@RequiredArgsConstructor
public class RoutingEngine {

    private final List<RoutingRule> rules;

    public RoutingResult decide(@NotNull RoutingContext context) {
        for (RoutingRule rule : rules) {
            Optional<RoutingResult> result = rule.evaluate(context);
            if (result.isPresent()) {
                return result.get();
            }
        }
        // Son kural (LowestCostRule) her zaman karar verir; buraya gelinmesi kural listesinin eksik olduğunu gösterir
        return RoutingResult.reject(RoutingReason.NO_ACTIVE_BANK);
    }
}
