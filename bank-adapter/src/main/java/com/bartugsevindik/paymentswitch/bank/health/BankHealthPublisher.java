/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.bank.health;

import com.bartugsevindik.paymentswitch.bank.config.BankAdapterProperties;
import com.bartugsevindik.paymentswitch.common.enums.BankCode;
import com.bartugsevindik.paymentswitch.common.event.BankHealthChangedEvent;
import com.bartugsevindik.paymentswitch.messaging.MessageHeaders;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/**
 * <h1>BankHealthPublisher</h1>
 * <p>Circuit breaker durumu değiştiğinde {@code bank.health} topic'ine bildirir. routing-service bankayı bu bilgiyle
 * otomatik olarak devre dışı bırakır ya da geri açar.</p>
 * <p>Outbox kullanılmaz: bu bir iş verisi değil, anlık durumdur. Mesaj kaybolursa periyodik bildirim
 * ({@link #publishAll()}) durumu düzeltir.</p>
 * <p>Sadece {@code CLOSED} sağlıklı sayılır. {@code HALF_OPEN}'da tüm trafik geri gelirse circuit'in izin verdiği
 * birkaç deneme dışındaki işlemler reddedilirdi; deneme çağrılarını echo probu yapar.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-5
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BankHealthPublisher {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final BankAdapterProperties properties;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @PostConstruct
    void subscribe() {
        for (BankCode bank : properties.getBanks()) {
            circuitBreakerRegistry.circuitBreaker(bank.name()).getEventPublisher().onStateTransition(event -> {
                CircuitBreaker.State state = event.getStateTransition().getToState();
                log.warn("Circuit breaker state changed. bank={}, transition={}", bank, event.getStateTransition());
                publish(bank, state);
            });
        }
    }

    public void publishAll() {
        for (BankCode bank : properties.getBanks()) {
            publish(bank, circuitBreakerRegistry.circuitBreaker(bank.name()).getState());
        }
    }

    private void publish(BankCode bank, CircuitBreaker.State state) {
        BankHealthChangedEvent event = new BankHealthChangedEvent(bank, state == CircuitBreaker.State.CLOSED, state.name(), Instant.now());
        try {
            ProducerRecord<String, String> record = new ProducerRecord<>(BankHealthChangedEvent.TOPIC, bank.name(),
                    objectMapper.writeValueAsString(event));
            record.headers().add(MessageHeaders.EVENT_ID, UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
            kafkaTemplate.send(record).whenComplete((result, ex) -> {
                if (ex != null) {
                    log.warn("Bank health could not be published, next periodic publish will retry. bank={}", bank, ex);
                }
            });
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("Bank health could not be published. bank={}", bank, e);
        }
    }
}
