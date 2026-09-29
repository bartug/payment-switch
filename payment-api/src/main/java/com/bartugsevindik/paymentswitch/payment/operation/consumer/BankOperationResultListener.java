/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.operation.consumer;

import com.bartugsevindik.paymentswitch.common.event.BankOperationResultEvent;
import com.bartugsevindik.paymentswitch.messaging.consumer.EventReader;
import com.bartugsevindik.paymentswitch.payment.operation.service.PaymentOperationService;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * <h1>BankOperationResultListener</h1>
 * <p>bank-adapter'ın iptal ve iade sonuçlarını dinler.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 29.09.2026 - PS-6
 */
@Component
@RequiredArgsConstructor
public class BankOperationResultListener {

    private final EventReader eventReader;
    private final PaymentOperationService paymentOperationService;

    @KafkaListener(topics = BankOperationResultEvent.TOPIC)
    public void onOperationResult(ConsumerRecord<String, String> record) {
        paymentOperationService.applyResult(eventReader.read(record, BankOperationResultEvent.class));
    }
}
