/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.routing.consumer;

import com.bartugsevindik.paymentswitch.common.event.PaymentRequestedEvent;
import com.bartugsevindik.paymentswitch.messaging.consumer.EventReader;
import com.bartugsevindik.paymentswitch.routing.service.RoutingService;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * <h1>PaymentRequestedListener</h1>
 * <p>{@code payment.requested} topic'ini dinler. Mesaj key'i paymentId olduğu için aynı ödemenin mesajları
 * aynı partition'a, dolayısıyla aynı thread'e düşer.</p>
 * <p>Listener hata fırlatırsa mesaj retry edilir, olmazsa {@code payment.requested.DLT}'ye gider.
 * Offset ancak listener başarıyla dönünce commit edilir (at-least-once).</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-4
 */
@Component
@RequiredArgsConstructor
public class PaymentRequestedListener {

    private final EventReader eventReader;
    private final RoutingService routingService;

    @KafkaListener(topics = PaymentRequestedEvent.TOPIC)
    public void onPaymentRequested(ConsumerRecord<String, String> record) {
        routingService.route(eventReader.read(record, PaymentRequestedEvent.class));
    }
}
