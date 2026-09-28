/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.routing.service;

import com.bartugsevindik.paymentswitch.common.event.PaymentRequestedEvent;
import com.bartugsevindik.paymentswitch.messaging.consumer.IncomingEvent;
import com.bartugsevindik.paymentswitch.routing.dto.RoutingResultDTO;
import com.bartugsevindik.paymentswitch.routing.dto.RoutingSimulationRequest;
import org.springframework.stereotype.Service;

/**
 * <h1>RoutingService</h1>
 * <p>Ödemelerin hangi bankaya gideceğine karar verilmesi.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-4
 */
@Service
public interface RoutingService {

    /**
     * <h1>Ödeme Yönlendirme</h1>
     * <p>Kararı verir ve tek transaction içinde kaydeder: inbox kaydı, routing kararı, banka topic'ine giden istek
     * ve payment-api'ye giden sonuç. Aynı event ikinci kez gelirse hiçbir şey yapılmaz.</p>
     *
     * @param event {@code payment.requested} topic'inden gelen event
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-4
     */
    void route(IncomingEvent<PaymentRequestedEvent> event);

    /**
     * <h1>Routing Simülasyonu</h1>
     * <p>Kararı kaydetmeden ve event üretmeden döndürür. Kuralları ve banka durumlarını denemek için.</p>
     *
     * @param request BIN ve taksit sayısı
     * @return Karar ve karara giden bilgiler
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-4
     */
    RoutingResultDTO simulate(RoutingSimulationRequest request);
}
