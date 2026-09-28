/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.outbox;

import com.bartugsevindik.paymentswitch.common.event.PaymentRequestedEvent;
import com.bartugsevindik.paymentswitch.payment.outbox.entity.OutboxEvent;
import com.bartugsevindik.paymentswitch.payment.outbox.relay.OutboxRelay;
import com.bartugsevindik.paymentswitch.payment.outbox.repository.OutboxEventRepository;
import com.bartugsevindik.paymentswitch.payment.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OutboxIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Test
    void odemeEventiKafkayaGider() throws Exception {
        TestTerminal terminal = newTerminal();
        String paymentId = paymentId(mockMvc.perform(signedPost(terminal, UUID.randomUUID().toString(), VALID_REQUEST))
                .andExpect(status().isAccepted()).andReturn().getResponse());

        assertThat(outboxEventRepository.findFirstByAggregateIdOrderByIdDesc(paymentId)
                .map(OutboxEvent::getPublishedAt)).isEmpty();

        outboxRelay.publishPending();

        assertThat(outboxEventRepository.findFirstByAggregateIdOrderByIdDesc(paymentId)
                .map(OutboxEvent::getPublishedAt)).isPresent();

        ConsumerRecord<String, String> record = consumeByKey(paymentId);
        assertThat(record.key()).isEqualTo(paymentId);
        assertThat(header(record, OutboxRelay.EVENT_TYPE_HEADER)).isEqualTo("PaymentRequestedEvent");
        assertThat(header(record, OutboxRelay.EVENT_ID_HEADER))
                .isEqualTo(outboxEventRepository.findFirstByAggregateIdOrderByIdDesc(paymentId).orElseThrow().getEventId());

        JsonNode payload = objectMapper.readTree(record.value());
        assertThat(payload.get("paymentId").asText()).isEqualTo(paymentId);
        assertThat(payload.get("merchantId").asText()).isEqualTo(terminal.merchantId());
        assertThat(payload.get("amount").asLong()).isEqualTo(125050);
        assertThat(payload.get("cardBin").asText()).isEqualTo("54006170");
        assertThat(payload.get("installmentCount").asInt()).isEqualTo(3);
        // Kart numarası ve CVV Kafka'ya asla yazılmamalı (PCI DSS)
        assertThat(record.value()).doesNotContain("5400617020092306").doesNotContain("cvv");
    }

    @Test
    void reddedilenIstekOutboxaYazilmaz() throws Exception {
        long before = outboxEventRepository.count();

        mockMvc.perform(signedPost(newTerminal(), UUID.randomUUID().toString(),
                        VALID_REQUEST.replace("5400617020092306", "5400617020092307")))
                .andExpect(status().isBadRequest());

        assertThat(outboxEventRepository.count()).isEqualTo(before);
    }

    @Test
    void tekrarEdenIstekIkinciEventUretmez() throws Exception {
        TestTerminal terminal = newTerminal();
        String key = UUID.randomUUID().toString();
        String paymentId = paymentId(mockMvc.perform(signedPost(terminal, key, VALID_REQUEST)).andReturn().getResponse());
        mockMvc.perform(signedPost(terminal, key, VALID_REQUEST)).andExpect(status().isAccepted());

        assertThat(outboxEventRepository.findAll().stream().filter(e -> e.getAggregateId().equals(paymentId))).hasSize(1);
    }

    private ConsumerRecord<String, String> consumeByKey(String key) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        AtomicReference<ConsumerRecord<String, String>> found = new AtomicReference<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
            consumer.subscribe(List.of(PaymentRequestedEvent.TOPIC));
            await().atMost(Duration.ofSeconds(15)).until(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(r -> {
                    if (key.equals(r.key())) {
                        found.set(r);
                    }
                });
                return found.get() != null;
            });
        }
        return found.get();
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        return Optional.ofNullable(record.headers().lastHeader(name))
                .map(h -> new String(h.value(), StandardCharsets.UTF_8))
                .orElse(null);
    }
}
