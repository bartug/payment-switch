/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.operation;

import com.bartugsevindik.paymentswitch.common.enums.BankCode;
import com.bartugsevindik.paymentswitch.common.enums.BankOperationType;
import com.bartugsevindik.paymentswitch.common.enums.BankResultStatus;
import com.bartugsevindik.paymentswitch.common.enums.OperationResultStatus;
import com.bartugsevindik.paymentswitch.common.event.BankAuthorizationResultEvent;
import com.bartugsevindik.paymentswitch.common.event.BankOperationResultEvent;
import com.bartugsevindik.paymentswitch.messaging.MessageHeaders;
import com.bartugsevindik.paymentswitch.messaging.outbox.OutboxEvent;
import com.bartugsevindik.paymentswitch.messaging.outbox.OutboxEventRepository;
import com.bartugsevindik.paymentswitch.payment.controller.PaymentController;
import com.bartugsevindik.paymentswitch.payment.entity.Payment;
import com.bartugsevindik.paymentswitch.payment.enums.PaymentStatus;
import com.bartugsevindik.paymentswitch.payment.operation.entity.PaymentOperation;
import com.bartugsevindik.paymentswitch.payment.operation.repository.PaymentOperationRepository;
import com.bartugsevindik.paymentswitch.payment.repository.PaymentRepository;
import com.bartugsevindik.paymentswitch.payment.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ödeme tutarı 1250,50 TL (VALID_REQUEST).
 */
@TestPropertySource(properties = "application.payment-operation.business-day-cutoff=23:59:59")
class PaymentOperationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentOperationRepository paymentOperationRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void kismiIadeSonraKalaninIadesi() throws Exception {
        TestTerminal terminal = newTerminal();
        String paymentId = approvedPayment(terminal);

        String first = operationId(refund(terminal, paymentId, "500.00").andExpect(status().isAccepted())
                .andExpect(jsonPath("$.object.status").value("PENDING")).andReturn().getResponse());

        // İade isteği satışla aynı banka topic'ine, aynı key ile gidiyor
        OutboxEvent request = outboxEventRepository.findFirstByAggregateIdOrderByIdDesc(paymentId).orElseThrow();
        assertThat(request.getTopic()).isEqualTo("bank.requests.YKB");
        assertThat(request.getMessageKey()).isEqualTo(paymentId);
        JsonNode payload = objectMapper.readTree(request.getPayload());
        assertThat(payload.get("type").asText()).isEqualTo("REFUND");
        assertThat(payload.get("amount").asLong()).isEqualTo(50000);

        publishResult(first, paymentId, BankOperationType.REFUND, OperationResultStatus.SUCCEEDED);
        Payment partial = awaitStatus(paymentId, PaymentStatus.PARTIALLY_REFUNDED);
        assertThat(partial.getRefundedAmount()).isEqualTo(50000);

        String second = operationId(refund(terminal, paymentId, "750.50").andExpect(status().isAccepted()).andReturn().getResponse());
        publishResult(second, paymentId, BankOperationType.REFUND, OperationResultStatus.SUCCEEDED);
        assertThat(awaitStatus(paymentId, PaymentStatus.REFUNDED).getRefundedAmount()).isEqualTo(125050);
    }

    @Test
    void iadeTutariOdemeyiAsamaz() throws Exception {
        TestTerminal terminal = newTerminal();
        String paymentId = approvedPayment(terminal);

        refund(terminal, paymentId, "1250.51")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message", containsString("İade edilebilir: 1250.50 TRY")));
    }

    @Test
    void sonucuBekleyenIadeTutarAyrilmisSayilir() throws Exception {
        TestTerminal terminal = newTerminal();
        String paymentId = approvedPayment(terminal);

        refund(terminal, paymentId, "1000.00").andExpect(status().isAccepted());
        // İlk iadenin bankadan sonucu henüz gelmedi; ikincisi de kabul edilseydi toplam 1300 olurdu
        refund(terminal, paymentId, "300.00")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message", containsString("250.50 TRY")));
    }

    @Test
    void esZamanliIkiIadeToplamiAsamaz() throws Exception {
        TestTerminal terminal = newTerminal();
        String paymentId = approvedPayment(terminal);

        CountDownLatch start = new CountDownLatch(1);
        List<MockHttpServletResponse> responses = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<MockHttpServletResponse>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return refund(terminal, paymentId, "700.00").andReturn().getResponse();
                }));
            }
            start.countDown();
            for (Future<MockHttpServletResponse> future : futures) {
                responses.add(future.get());
            }
        }

        // Satır kilidi olmasaydı ikisi de "kalan 1250,50" görüp kabul edilirdi
        assertThat(responses).extracting(MockHttpServletResponse::getStatus).containsExactlyInAnyOrder(202, 422);
        assertThat(paymentOperationRepository.findByPaymentIdOrderByIdAsc(paymentId)).hasSize(1);
    }

    @Test
    void ayniKeyIleTekrarGelenIadeIkinciIadeOlusturmaz() throws Exception {
        TestTerminal terminal = newTerminal();
        String paymentId = approvedPayment(terminal);
        String key = UUID.randomUUID().toString();

        String first = operationId(refund(terminal, paymentId, "100.00", key).andReturn().getResponse());
        String second = operationId(refund(terminal, paymentId, "100.00", key)
                .andExpect(status().isAccepted())
                .andExpect(header().string(PaymentController.IDEMPOTENT_REPLAYED_HEADER, "true"))
                .andReturn().getResponse());

        assertThat(second).isEqualTo(first);
        assertThat(paymentOperationRepository.findByPaymentIdOrderByIdAsc(paymentId)).hasSize(1);
    }

    @Test
    void ayniGunIptalEdilir() throws Exception {
        TestTerminal terminal = newTerminal();
        String paymentId = approvedPayment(terminal);

        String operationId = operationId(voidPayment(terminal, paymentId).andExpect(status().isAccepted()).andReturn().getResponse());
        assertThat(paymentRepository.findByPaymentId(paymentId).orElseThrow().getPaymentStatus()).isEqualTo(PaymentStatus.VOIDING);

        // İptal devam ederken iade yapılamaz
        refund(terminal, paymentId, "10.00").andExpect(status().isConflict());

        publishResult(operationId, paymentId, BankOperationType.VOID, OperationResultStatus.SUCCEEDED);
        awaitStatus(paymentId, PaymentStatus.VOIDED);
    }

    @Test
    void bankaIptaliReddederseOdemeOnayliKalir() throws Exception {
        TestTerminal terminal = newTerminal();
        String paymentId = approvedPayment(terminal);
        String operationId = operationId(voidPayment(terminal, paymentId).andReturn().getResponse());

        publishResult(operationId, paymentId, BankOperationType.VOID, OperationResultStatus.FAILED);

        awaitStatus(paymentId, PaymentStatus.APPROVED);
        PaymentOperation operation = paymentOperationRepository.findByOperationId(operationId).orElseThrow();
        assertThat(operation.getFailureReason()).isEqualTo("test");
    }

    @Test
    void iadeYapilmisOdemeIptalEdilemez() throws Exception {
        TestTerminal terminal = newTerminal();
        String paymentId = approvedPayment(terminal);
        String refundId = operationId(refund(terminal, paymentId, "10.00").andReturn().getResponse());
        publishResult(refundId, paymentId, BankOperationType.REFUND, OperationResultStatus.SUCCEEDED);
        awaitStatus(paymentId, PaymentStatus.PARTIALLY_REFUNDED);

        voidPayment(terminal, paymentId)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message", containsString("onaylı")));
    }

    @Test
    void onaylanmamisOdemeIadeEdilemez() throws Exception {
        TestTerminal terminal = newTerminal();
        String paymentId = paymentId(mockMvc.perform(signedPost(terminal, UUID.randomUUID().toString(), VALID_REQUEST))
                .andReturn().getResponse());

        refund(terminal, paymentId, "10.00").andExpect(status().isUnprocessableEntity());
        voidPayment(terminal, paymentId).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void baskaUyeIsyerininOdemesiIadeEdilemez() throws Exception {
        String paymentId = approvedPayment(newTerminal());

        refund(newTerminal(), paymentId, "10.00").andExpect(status().isNotFound());
    }

    private String approvedPayment(TestTerminal terminal) throws Exception {
        String paymentId = paymentId(mockMvc.perform(signedPost(terminal, UUID.randomUUID().toString(), VALID_REQUEST))
                .andExpect(status().isAccepted()).andReturn().getResponse());
        BankAuthorizationResultEvent event = new BankAuthorizationResultEvent(paymentId, BankCode.YKB, BankResultStatus.APPROVED,
                "00", "482915", "627104839215", "Onaylandı", Instant.now());
        send(BankAuthorizationResultEvent.TOPIC, paymentId, objectMapper.writeValueAsString(event));
        awaitStatus(paymentId, PaymentStatus.APPROVED);
        return paymentId;
    }

    private ResultActions refund(TestTerminal terminal, String paymentId, String amount) throws Exception {
        return refund(terminal, paymentId, amount, UUID.randomUUID().toString());
    }

    private ResultActions refund(TestTerminal terminal, String paymentId, String amount, String key) throws Exception {
        return mockMvc.perform(signedPostTo(terminal, "/v1/payments/" + paymentId + "/refunds", key,
                "{\"amount\": " + amount + "}"));
    }

    private ResultActions voidPayment(TestTerminal terminal, String paymentId) throws Exception {
        return mockMvc.perform(signedPostTo(terminal, "/v1/payments/" + paymentId + "/void", UUID.randomUUID().toString(), ""));
    }

    private void publishResult(String operationId, String paymentId, BankOperationType type, OperationResultStatus status) throws Exception {
        BankOperationResultEvent event = new BankOperationResultEvent(operationId, paymentId, type, status,
                status == OperationResultStatus.SUCCEEDED ? "00" : "12", "test", Instant.now());
        send(BankOperationResultEvent.TOPIC, paymentId, objectMapper.writeValueAsString(event));
    }

    private void send(String topic, String key, String value) throws Exception {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, value);
        record.headers().add(MessageHeaders.EVENT_ID, UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
        kafkaTemplate.send(record).get();
    }

    private Payment awaitStatus(String paymentId, PaymentStatus status) {
        return await().atMost(Duration.ofSeconds(15)).until(
                () -> paymentRepository.findByPaymentId(paymentId).orElseThrow(), p -> p.getPaymentStatus() == status);
    }

    private String operationId(MockHttpServletResponse response) {
        try {
            return objectMapper.readTree(response.getContentAsString()).at("/object/operationId").asText();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
