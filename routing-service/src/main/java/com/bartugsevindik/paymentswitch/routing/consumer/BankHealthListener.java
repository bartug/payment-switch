/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.routing.consumer;

import com.bartugsevindik.paymentswitch.common.event.BankHealthChangedEvent;
import com.bartugsevindik.paymentswitch.messaging.consumer.EventReader;
import com.bartugsevindik.paymentswitch.routing.service.AcquirerBankService;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * <h1>BankHealthListener</h1>
 * <p>bank-adapter'daki circuit breaker durumlarını dinler. Circuit açılınca banka otomatik olarak routing dışında
 * kalır, kapanınca geri döner.</p>
 * <p>Event bir durum bildirimi olduğu için inbox kullanılmaz; aynı durumu iki kez uygulamak zararsızdır.
 * Topic tek partition'dır, durumlar sırayla işlenir.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-5
 */
@Component
@RequiredArgsConstructor
public class BankHealthListener {

    private final EventReader eventReader;
    private final AcquirerBankService acquirerBankService;

    @KafkaListener(topics = BankHealthChangedEvent.TOPIC, concurrency = "1")
    public void onBankHealthChanged(ConsumerRecord<String, String> record) {
        BankHealthChangedEvent event = eventReader.read(record, BankHealthChangedEvent.class).payload();
        acquirerBankService.updateBankHealth(event.bankCode(), event.healthy());
    }
}
