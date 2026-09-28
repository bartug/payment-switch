/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.consumer;

import com.bartugsevindik.paymentswitch.common.event.BankAuthorizationResultEvent;
import com.bartugsevindik.paymentswitch.messaging.consumer.EventReader;
import com.bartugsevindik.paymentswitch.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * <h1>BankAuthorizationResultListener</h1>
 * <p>bank-adapter'ın banka sonuçlarını dinler ve ödemenin durumunu günceller.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-5
 */
@Component
@RequiredArgsConstructor
public class BankAuthorizationResultListener {

    private final EventReader eventReader;
    private final PaymentService paymentService;

    @KafkaListener(topics = BankAuthorizationResultEvent.TOPIC)
    public void onBankResult(ConsumerRecord<String, String> record) {
        paymentService.applyBankResult(eventReader.read(record, BankAuthorizationResultEvent.class));
    }
}
