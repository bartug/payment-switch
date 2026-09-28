/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.routing.rule;

import java.util.Optional;

/**
 * <h1>RoutingRule</h1>
 * <p>Routing kural zincirinin bir halkası (Chain of Responsibility). Kurallar {@code @Order} sırasıyla çalışır;
 * karar veren ilk kural zinciri bitirir, boş dönen kural kararı bir sonrakine bırakır.</p>
 * <p>Yeni bir kural eklemek için bu arayüzü uygulayan bir {@code @Component} yazmak yeterlidir.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-4
 */
public interface RoutingRule {

    Optional<RoutingResult> evaluate(RoutingContext context);
}
