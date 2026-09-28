/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.consumer;

import com.bartugsevindik.paymentswitch.common.event.PaymentRoutingResultEvent;
import com.bartugsevindik.paymentswitch.messaging.consumer.EventReader;
import com.bartugsevindik.paymentswitch.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * <h1>PaymentRoutingResultListener</h1>
 * <p>routing-service'in kararlarını dinler ve ödemenin durumunu günceller.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-4
 */
@Component
@RequiredArgsConstructor
public class PaymentRoutingResultListener {

    private final EventReader eventReader;
    private final PaymentService paymentService;

    @KafkaListener(topics = PaymentRoutingResultEvent.TOPIC)
    public void onRoutingResult(ConsumerRecord<String, String> record) {
        paymentService.applyRoutingResult(eventReader.read(record, PaymentRoutingResultEvent.class));
    }
}
