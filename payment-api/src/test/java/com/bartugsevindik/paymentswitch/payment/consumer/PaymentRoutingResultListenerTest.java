/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.consumer;

import com.bartugsevindik.paymentswitch.common.enums.BankCode;
import com.bartugsevindik.paymentswitch.common.event.PaymentRoutingResultEvent;
import com.bartugsevindik.paymentswitch.messaging.MessageHeaders;
import com.bartugsevindik.paymentswitch.payment.entity.Payment;
import com.bartugsevindik.paymentswitch.payment.enums.PaymentStatus;
import com.bartugsevindik.paymentswitch.payment.repository.PaymentRepository;
import com.bartugsevindik.paymentswitch.payment.support.AbstractIntegrationTest;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PaymentRoutingResultListenerTest extends AbstractIntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    void yonlendirilenOdemeRoutedOlur() throws Exception {
        TestTerminal terminal = newTerminal();
        String paymentId = createPayment(terminal);

        publish(UUID.randomUUID().toString(), new PaymentRoutingResultEvent(paymentId, true, BankCode.YKB, true,
                "ON_US_INSTALLMENT", "Taksitli işlem kartın program bankasına yönlendirildi.", Instant.now()));

        awaitStatus(paymentId, PaymentStatus.ROUTED);
        mockMvc.perform(signedGet(terminal, "/v1/payments/" + paymentId))
                .andExpect(jsonPath("$.object.paymentStatus").value("ROUTED"))
                .andExpect(jsonPath("$.object.bankCode").value("YKB"));
    }

    @Test
    void reddedilenOdemeFailedOlurSebebiYazilir() throws Exception {
        TestTerminal terminal = newTerminal();
        String paymentId = createPayment(terminal);

        publish(UUID.randomUUID().toString(), new PaymentRoutingResultEvent(paymentId, false, null, false,
                "NON_CREDIT_INSTALLMENT", "Banka kartı ve ön ödemeli kartlarla taksitli işlem yapılamaz.", Instant.now()));

        Payment payment = awaitStatus(paymentId, PaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).contains("taksitli işlem yapılamaz");
        mockMvc.perform(signedGet(terminal, "/v1/payments/" + paymentId))
                .andExpect(jsonPath("$.object.paymentStatus").value("FAILED"))
                .andExpect(jsonPath("$.object.failureReason").value(payment.getFailureReason()));
    }

    @Test
    void ayniEventIkiKezGelirseHataOlmaz() throws Exception {
        String paymentId = createPayment(newTerminal());
        String eventId = UUID.randomUUID().toString();
        PaymentRoutingResultEvent event = new PaymentRoutingResultEvent(paymentId, true, BankCode.QNB, false,
                "LOWEST_COST", "", Instant.now());

        publish(eventId, event);
        publish(eventId, event);
        awaitStatus(paymentId, PaymentStatus.ROUTED);

        // İkinci mesaj inbox'a takılmasaydı ROUTED → ROUTED geçişi exception fırlatır ve mesaj DLT'ye giderdi
        String marker = createPayment(newTerminal());
        publish(UUID.randomUUID().toString(), new PaymentRoutingResultEvent(marker, true, BankCode.QNB, false,
                "LOWEST_COST", "", Instant.now()));
        awaitStatus(marker, PaymentStatus.ROUTED);
        assertThat(paymentRepository.findByPaymentId(paymentId).orElseThrow().getBankCode()).isEqualTo(BankCode.QNB);
    }

    private String createPayment(TestTerminal terminal) throws Exception {
        return paymentId(mockMvc.perform(signedPost(terminal, UUID.randomUUID().toString(), VALID_REQUEST))
                .andExpect(status().isAccepted()).andReturn().getResponse());
    }

    private void publish(String eventId, PaymentRoutingResultEvent event) throws Exception {
        ProducerRecord<String, String> record = new ProducerRecord<>(PaymentRoutingResultEvent.TOPIC, event.paymentId(),
                objectMapper.writeValueAsString(event));
        record.headers().add(MessageHeaders.EVENT_ID, eventId.getBytes(StandardCharsets.UTF_8));
        kafkaTemplate.send(record).get();
    }

    private Payment awaitStatus(String paymentId, PaymentStatus status) {
        return await().atMost(Duration.ofSeconds(15)).until(
                () -> paymentRepository.findByPaymentId(paymentId).orElseThrow(),
                payment -> payment.getPaymentStatus() == status);
    }
}
