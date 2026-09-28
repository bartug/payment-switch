/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.outbox;

import com.bartugsevindik.paymentswitch.messaging.outbox.OutboxEvent;
import com.bartugsevindik.paymentswitch.messaging.outbox.OutboxEventRepository;
import com.bartugsevindik.paymentswitch.payment.support.AbstractIntegrationTest;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kafka'ya yazılamadığında ödeme almaya devam edilmeli; event outbox'ta birikmeli ve Kafka geri gelince gönderilmeli.
 */
class OutboxKafkaDownTest extends AbstractIntegrationTest {

    @MockitoBean
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Test
    void kafkaKapaliykenOdemeAlinirEventBekler() throws Exception {
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new TimeoutException("Topic payment.requested not present in metadata")));

        String paymentId = paymentId(mockMvc.perform(signedPost(newTerminal(), UUID.randomUUID().toString(), VALID_REQUEST))
                .andExpect(status().isAccepted())
                .andReturn().getResponse());

        outboxRelay.publishPending();
        outboxRelay.publishPending();

        OutboxEvent pending = outboxEventRepository.findFirstByAggregateIdOrderByIdDesc(paymentId).orElseThrow();
        assertThat(pending.getPublishedAt()).isNull();
        assertThat(pending.getAttempts()).isEqualTo(2);
        assertThat(pending.getLastError()).contains("TimeoutException");

        // Kafka geri geldi
        reset(kafkaTemplate);
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenAnswer(invocation -> CompletableFuture.completedFuture(new SendResult<>(invocation.getArgument(0), null)));

        outboxRelay.publishPending();

        OutboxEvent published = outboxEventRepository.findFirstByAggregateIdOrderByIdDesc(paymentId).orElseThrow();
        assertThat(published.getPublishedAt()).isNotNull();
        assertThat(published.getAttempts()).isEqualTo(3);
        assertThat(published.getLastError()).isNull();
    }
}
