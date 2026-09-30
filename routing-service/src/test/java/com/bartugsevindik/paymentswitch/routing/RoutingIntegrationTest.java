/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.routing;

import com.bartugsevindik.paymentswitch.common.enums.BankCode;
import com.bartugsevindik.paymentswitch.common.enums.TerminalType;
import com.bartugsevindik.paymentswitch.common.event.BankHealthChangedEvent;
import com.bartugsevindik.paymentswitch.common.event.PaymentRequestedEvent;
import com.bartugsevindik.paymentswitch.common.event.PaymentRoutingResultEvent;
import com.bartugsevindik.paymentswitch.messaging.MessageHeaders;
import com.bartugsevindik.paymentswitch.messaging.outbox.OutboxRelay;
import com.bartugsevindik.paymentswitch.routing.entity.RoutingDecision;
import com.bartugsevindik.paymentswitch.routing.enums.RoutingReason;
import com.bartugsevindik.paymentswitch.routing.repository.RoutingDecisionRepository;
import com.bartugsevindik.paymentswitch.routing.service.AcquirerBankService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = {"application.scheduling.enabled=false", "management.otlp.tracing.export.enabled=false",
        "application.outbox.wake-up-on-commit=false"})
class RoutingIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka-native:3.8.1");

    static {
        POSTGRES.start();
        KAFKA.start();
    }

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OutboxRelay outboxRelay;

    @Autowired
    private RoutingDecisionRepository routingDecisionRepository;

    @Autowired
    private AcquirerBankService acquirerBankService;

    @AfterEach
    void activateAllBanks() {
        for (BankCode bank : BankCode.values()) {
            acquirerBankService.updateBankStatus(bank, true);
            acquirerBankService.updateBankHealth(bank, true);
        }
    }

    @Test
    void worldKartTaksitliIslemYkbTopicineGider() throws Exception {
        String paymentId = publish(UUID.randomUUID().toString(), "54006170", 3);

        RoutingDecision decision = awaitDecision(paymentId);
        assertThat(decision.getBankCode()).isEqualTo(BankCode.YKB);
        assertThat(decision.getReason()).isEqualTo(RoutingReason.ON_US_INSTALLMENT);

        outboxRelay.publishPending();

        JsonNode bankRequest = objectMapper.readTree(consumeByKey(BankCode.YKB.requestTopic(), paymentId).value());
        assertThat(bankRequest.get("bankCode").asText()).isEqualTo("YKB");
        assertThat(bankRequest.get("onUs").asBoolean()).isTrue();
        assertThat(bankRequest.get("installmentCount").asInt()).isEqualTo(3);

        JsonNode result = objectMapper.readTree(consumeByKey(PaymentRoutingResultEvent.TOPIC, paymentId).value());
        assertThat(result.get("routed").asBoolean()).isTrue();
        assertThat(result.get("bankCode").asText()).isEqualTo("YKB");
    }

    @Test
    void bankaKartiIleTaksitReddedilirBankayaIstekGitmez() throws Exception {
        String paymentId = publish(UUID.randomUUID().toString(), "97920012", 6);

        RoutingDecision decision = awaitDecision(paymentId);
        assertThat(decision.getBankCode()).isNull();
        assertThat(decision.getReason()).isEqualTo(RoutingReason.NON_CREDIT_INSTALLMENT);

        outboxRelay.publishPending();
        JsonNode result = objectMapper.readTree(consumeByKey(PaymentRoutingResultEvent.TOPIC, paymentId).value());
        assertThat(result.get("routed").asBoolean()).isFalse();
        assertThat(result.get("reason").asText()).isEqualTo("NON_CREDIT_INSTALLMENT");
    }

    @Test
    void sekizHaneliBinAltiHaneliyiEzer() {
        // 400005 → Garanti kredi kartı, 40000566 → YKB banka kartı. Taksitte banka kartı olduğu için reddedilmeli.
        String paymentId = publish(UUID.randomUUID().toString(), "40000566", 3);

        assertThat(awaitDecision(paymentId).getReason()).isEqualTo(RoutingReason.NON_CREDIT_INSTALLMENT);
    }

    @Test
    void kartinBankasiPasifkenTekCekimFailoverYapar() {
        acquirerBankService.updateBankStatus(BankCode.YKB, false);

        String paymentId = publish(UUID.randomUUID().toString(), "54006170", 1);

        RoutingDecision decision = awaitDecision(paymentId);
        assertThat(decision.getBankCode()).isEqualTo(BankCode.QNB);
        assertThat(decision.getOnUs()).isFalse();
    }

    @Test
    void circuitAcilincaBankaOtomatikDevreDisiKalir() throws Exception {
        publishHealth(BankCode.YKB, false, "OPEN");
        await().atMost(Duration.ofSeconds(15)).until(() -> acquirerBankService.getActiveBanks().stream()
                .noneMatch(bank -> bank.bankCode() == BankCode.YKB));

        String single = publish(UUID.randomUUID().toString(), "54006170", 1);
        String installment = publish(UUID.randomUUID().toString(), "54006170", 3);
        assertThat(awaitDecision(single).getBankCode()).isEqualTo(BankCode.QNB);
        assertThat(awaitDecision(installment).getReason()).isEqualTo(RoutingReason.PROGRAM_BANK_UNAVAILABLE);

        publishHealth(BankCode.YKB, true, "CLOSED");
        await().atMost(Duration.ofSeconds(15)).until(() -> acquirerBankService.getActiveBanks().stream()
                .anyMatch(bank -> bank.bankCode() == BankCode.YKB));
        // Operasyonun aktif/pasif kararı sağlık durumundan bağımsız
        assertThat(acquirerBankService.getAllBanks().stream().filter(b -> b.bankCode() == BankCode.YKB).findFirst()
                .orElseThrow().active()).isTrue();
    }

    @Test
    void ayniEventIkiKezGelirseTekKararVerilir() {
        String eventId = UUID.randomUUID().toString();
        String paymentId = publish(eventId, "411111", 1);
        publishWithId(eventId, paymentId, "411111", 1);

        awaitDecision(paymentId);
        // İkinci mesajın da işlenmesini bekle
        String marker = publish(UUID.randomUUID().toString(), "411111", 1);
        awaitDecision(marker);

        assertThat(routingDecisionRepository.findAll().stream().filter(d -> d.getPaymentId().equals(paymentId))).hasSize(1);
    }

    @Test
    void bozukMesajDltyeGider() {
        String paymentId = UUID.randomUUID().toString();
        ProducerRecord<String, String> record = new ProducerRecord<>(PaymentRequestedEvent.TOPIC, paymentId, "{bozuk json");
        record.headers().add(MessageHeaders.EVENT_ID, UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
        kafkaTemplate.send(record);

        ConsumerRecord<String, String> dlt = consumeByKey(PaymentRequestedEvent.TOPIC + ".DLT", paymentId);
        assertThat(dlt.value()).isEqualTo("{bozuk json");
        assertThat(MessageHeaders.read(dlt.headers(), "kafka_dlt-exception-fqcn"))
                .contains("ListenerExecutionFailedException");
    }

    private String publish(String eventId, String bin, int installment) {
        String paymentId = UUID.randomUUID().toString();
        publishWithId(eventId, paymentId, bin, installment);
        return paymentId;
    }

    private void publishWithId(String eventId, String paymentId, String bin, int installment) {
        PaymentRequestedEvent event = new PaymentRequestedEvent(paymentId, "MRC0000001", "TRM00000001",
                TerminalType.VIRTUAL, bin, "2306", UUID.randomUUID().toString(), 125050, "TRY", installment, Instant.now());
        try {
            ProducerRecord<String, String> record = new ProducerRecord<>(PaymentRequestedEvent.TOPIC, paymentId,
                    objectMapper.writeValueAsString(event));
            record.headers().add(MessageHeaders.EVENT_ID, eventId.getBytes(StandardCharsets.UTF_8));
            kafkaTemplate.send(record).get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void publishHealth(BankCode bank, boolean healthy, String state) throws Exception {
        ProducerRecord<String, String> record = new ProducerRecord<>(BankHealthChangedEvent.TOPIC, bank.name(),
                objectMapper.writeValueAsString(new BankHealthChangedEvent(bank, healthy, state, Instant.now())));
        record.headers().add(MessageHeaders.EVENT_ID, UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
        kafkaTemplate.send(record).get();
    }

    private RoutingDecision awaitDecision(String paymentId) {
        return await().atMost(Duration.ofSeconds(15))
                .until(() -> routingDecisionRepository.findByPaymentId(paymentId).orElse(null), d -> d != null);
    }

    private ConsumerRecord<String, String> consumeByKey(String topic, String key) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        List<ConsumerRecord<String, String>> found = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
            consumer.subscribe(List.of(topic));
            await().atMost(Duration.ofSeconds(20)).until(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(r -> {
                    if (key.equals(r.key())) {
                        found.add(r);
                    }
                });
                return !found.isEmpty();
            });
        }
        return found.getFirst();
    }
}
