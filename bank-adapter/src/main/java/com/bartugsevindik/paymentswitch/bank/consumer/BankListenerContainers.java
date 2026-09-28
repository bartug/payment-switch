/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.bank.consumer;

import com.bartugsevindik.paymentswitch.bank.config.BankAdapterProperties;
import com.bartugsevindik.paymentswitch.common.enums.BankCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * <h1>BankListenerContainers</h1>
 * <p>Her banka için ayrı listener container ve ayrı consumer group ({@code bank-adapter-YKB}) açar.</p>
 * <p>Tek bir {@code @KafkaListener} tüm banka topic'lerini dinleseydi, yavaş bir bankanın mesajı consumer
 * thread'ini bekletir ve aynı thread'e düşen diğer bankaların mesajları da beklerdi. Ayrı container'lar ile
 * YKB yavaşlasa bile QNB işlemleri etkilenmez. Ayrı consumer group ise bir bankadaki rebalance'ın diğerlerini
 * durdurmamasını sağlar.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-5
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BankListenerContainers implements SmartLifecycle {

    private final ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory;
    private final BankRequestListener bankRequestListener;
    private final BankAdapterProperties properties;
    private final List<ConcurrentMessageListenerContainer<String, String>> containers = new ArrayList<>();
    private volatile boolean running;

    @Override
    public void start() {
        for (BankCode bank : properties.getBanks()) {
            ConcurrentMessageListenerContainer<String, String> container =
                    kafkaListenerContainerFactory.createContainer(bank.requestTopic());
            container.getContainerProperties().setGroupId("bank-adapter-" + bank.name());
            container.getContainerProperties().setMessageListener(bankRequestListener);
            container.setConcurrency(properties.getConsumerConcurrency());
            container.setBeanName("bank-listener-" + bank.name());
            container.start();
            containers.add(container);
            log.info("Bank listener started. bank={}, topic={}, concurrency={}",
                    bank, bank.requestTopic(), properties.getConsumerConcurrency());
        }
        running = true;
    }

    @Override
    public void stop() {
        containers.forEach(ConcurrentMessageListenerContainer::stop);
        containers.clear();
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
