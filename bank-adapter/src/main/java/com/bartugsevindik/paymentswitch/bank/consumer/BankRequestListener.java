/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.bank.consumer;

import com.bartugsevindik.paymentswitch.bank.service.AuthorizationService;
import com.bartugsevindik.paymentswitch.common.event.BankAuthorizationRequestedEvent;
import com.bartugsevindik.paymentswitch.messaging.consumer.EventReader;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.stereotype.Component;

/**
 * <h1>BankRequestListener</h1>
 * <p>{@code bank.requests.{BANKA}} mesajlarını işler. {@code @KafkaListener} değil; her banka için ayrı container
 * {@link BankListenerContainers} tarafından açılır ve bu listener'ı kullanır.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-5
 */
@Component
@RequiredArgsConstructor
public class BankRequestListener implements MessageListener<String, String> {

    private final EventReader eventReader;
    private final AuthorizationService authorizationService;

    @Override
    public void onMessage(ConsumerRecord<String, String> record) {
        authorizationService.authorize(eventReader.read(record, BankAuthorizationRequestedEvent.class));
    }
}
