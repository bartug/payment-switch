/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.bank;

import com.bartugsevindik.paymentswitch.bank.entity.BankOperation;
import com.bartugsevindik.paymentswitch.bank.entity.BankTransaction;
import com.bartugsevindik.paymentswitch.bank.enums.BankOperationStatus;
import com.bartugsevindik.paymentswitch.bank.enums.BankTransactionStatus;
import com.bartugsevindik.paymentswitch.bank.repository.BankOperationRepository;
import com.bartugsevindik.paymentswitch.bank.repository.BankTransactionRepository;
import com.bartugsevindik.paymentswitch.common.enums.BankOperationType;
import com.bartugsevindik.paymentswitch.common.event.BankOperationRequestedEvent;
import com.bartugsevindik.paymentswitch.common.event.BankOperationResultEvent;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.bartugsevindik.paymentswitch.bank.service.RecoveryService;
import com.bartugsevindik.paymentswitch.common.enums.BankCode;
import com.bartugsevindik.paymentswitch.common.enums.TerminalType;
import com.bartugsevindik.paymentswitch.common.event.BankAuthorizationRequestedEvent;
import com.bartugsevindik.paymentswitch.common.event.BankAuthorizationResultEvent;
import com.bartugsevindik.paymentswitch.common.event.BankHealthChangedEvent;
import com.bartugsevindik.paymentswitch.messaging.MessageHeaders;
import com.bartugsevindik.paymentswitch.messaging.outbox.OutboxRelay;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Banka ve payment-api (card vault) WireMock ile taklit edilir; gecikme ve hata senaryoları kesin olarak kurulabilir.
 */
@SpringBootTest(properties = {
        "application.scheduling.enabled=false",
        "management.otlp.tracing.export.enabled=false",
        "application.outbox.wake-up-on-commit=false",
        "application.bank-adapter.banks=YKB,QNB",
        "application.bank-adapter.read-timeout=1s",
        "application.bank-adapter.recovery.inquiry-initial-delay=0s",
        "application.bank-adapter.recovery.inquiry-backoff=0s",
        "application.bank-adapter.recovery.max-inquiry-attempts=2",
        "application.bank-adapter.circuit-breaker.minimum-number-of-calls=3",
        "application.bank-adapter.circuit-breaker.sliding-window-size=3",
        "application.bank-adapter.circuit-breaker.wait-duration-in-open-state=1s"
})
class BankAdapterIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka-native:3.8.1");

    static {
        POSTGRES.start();
        KAFKA.start();
    }

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    @DynamicPropertySource
    static void urls(DynamicPropertyRegistry registry) {
        registry.add("application.bank-adapter.bank-api-url", wireMock::baseUrl);
        registry.add("application.bank-adapter.payment-api-url", wireMock::baseUrl);
    }

    private static final String CARD_JSON = """
            {"success": true, "message": "Kart verisi getirildi.",
             "object": {"pan": "5400617020092306", "expiryMonth": "12", "expiryYear": "28", "cvv": "000"}}
            """;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BankTransactionRepository bankTransactionRepository;

    @Autowired
    private RecoveryService recoveryService;

    @Autowired
    private OutboxRelay outboxRelay;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void stubCardVault() {
        wireMock.stubFor(post(urlPathMatching("/internal/v1/card-vault/.*/detokenize")).willReturn(okJson(CARD_JSON)));
    }

    @AfterEach
    void resetCircuits() {
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
    }

    @Test
    void onaylananIslemApprovedOlur() throws Exception {
        String paymentId = UUID.randomUUID().toString();
        stubAuthorize(paymentId, okJson(approved(paymentId)));

        publish(paymentId, BankCode.YKB);

        BankTransaction transaction = awaitStatus(paymentId, BankTransactionStatus.APPROVED);
        assertThat(transaction.getAuthCode()).isEqualTo("482915");

        outboxRelay.publishPending();
        JsonNode result = objectMapper.readTree(consumeByKey(BankAuthorizationResultEvent.TOPIC, paymentId).value());
        assertThat(result.get("status").asText()).isEqualTo("APPROVED");
        assertThat(result.get("rrn").asText()).isEqualTo("627104839215");

        // Card vault'a internal token ile gidildi
        wireMock.verify(postRequestedFor(urlPathMatching("/internal/v1/card-vault/.*/detokenize"))
                .withHeader("X-Internal-Token", com.github.tomakehurst.wiremock.client.WireMock.matching(".+")));
    }

    @Test
    void yetersizBakiyeDeclinedOlurCircuitiEtkilemez() {
        String paymentId = UUID.randomUUID().toString();
        stubAuthorize(paymentId, okJson("""
                {"orderId": "%s", "status": "DECLINED", "responseCode": "51", "message": "Yetersiz bakiye"}
                """.formatted(paymentId)));

        publish(paymentId, BankCode.YKB);

        assertThat(awaitStatus(paymentId, BankTransactionStatus.DECLINED).getResponseCode()).isEqualTo("51");
        assertThat(circuitBreakerRegistry.circuitBreaker("YKB").getMetrics().getNumberOfFailedCalls()).isZero();
    }

    @Test
    void timeoutOlanIslemRetryEdilmezInquiryIleOnaylanir() {
        String paymentId = UUID.randomUUID().toString();
        // Banka işlemi yapıyor ama cevap read-timeout'tan (1 sn) geç dönüyor
        stubAuthorize(paymentId, okJson(approved(paymentId)).withFixedDelay(2500));
        stubInquiry(paymentId, okJson(approved(paymentId)));

        publish(paymentId, BankCode.YKB);
        awaitStatus(paymentId, BankTransactionStatus.UNKNOWN);

        recoveryService.runOnce();

        assertThat(bankTransactionRepository.findByPaymentId(paymentId).orElseThrow().getStatus())
                .isEqualTo(BankTransactionStatus.APPROVED);
        // Satış isteği bankaya bir kez gitti; timeout sonrası tekrar gönderilmedi
        wireMock.verify(1, postRequestedFor(urlPathEqualTo("/banks/YKB/v1/authorize")));
    }

    @Test
    void bankaIslemiBulamazsaReversalYapilir() {
        String paymentId = UUID.randomUUID().toString();
        stubAuthorize(paymentId, aResponse().withStatus(503));
        stubInquiry(paymentId, aResponse().withStatus(404));
        stubReversal(paymentId, okJson("""
                {"orderId": "%s", "status": "REVERSED", "responseCode": "00", "message": "İptal edildi"}
                """.formatted(paymentId)));

        publish(paymentId, BankCode.YKB);
        awaitStatus(paymentId, BankTransactionStatus.UNKNOWN);

        recoveryService.runOnce();
        assertThat(bankTransactionRepository.findByPaymentId(paymentId).orElseThrow().getStatus())
                .isEqualTo(BankTransactionStatus.REVERSING);

        recoveryService.runOnce();
        assertThat(bankTransactionRepository.findByPaymentId(paymentId).orElseThrow().getStatus())
                .isEqualTo(BankTransactionStatus.REVERSED);
        wireMock.verify(1, postRequestedFor(urlPathEqualTo("/banks/YKB/v1/transactions/" + paymentId + "/reversal")));
    }

    @Test
    void inquiryDefalarcaBasarisizOlursaReversalaGecilir() {
        String paymentId = UUID.randomUUID().toString();
        stubAuthorize(paymentId, okJson(approved(paymentId)).withFixedDelay(2500));
        stubInquiry(paymentId, aResponse().withStatus(503));

        publish(paymentId, BankCode.YKB);
        awaitStatus(paymentId, BankTransactionStatus.UNKNOWN);

        recoveryService.runOnce();
        recoveryService.runOnce();

        BankTransaction transaction = bankTransactionRepository.findByPaymentId(paymentId).orElseThrow();
        assertThat(transaction.getStatus()).isEqualTo(BankTransactionStatus.REVERSING);
        assertThat(transaction.getLastError()).contains("Inquiry 2 denemede sonuç vermedi");
    }

    @Test
    void kartVerisiYoksaBankayaGidilmez() {
        String paymentId = UUID.randomUUID().toString();
        wireMock.stubFor(post(urlPathMatching("/internal/v1/card-vault/.*/detokenize"))
                .willReturn(aResponse().withStatus(404)));

        publish(paymentId, BankCode.YKB);

        assertThat(awaitStatus(paymentId, BankTransactionStatus.FAILED).getMessage()).contains("Kart verisi");
        wireMock.verify(0, postRequestedFor(urlPathEqualTo("/banks/YKB/v1/authorize")));
    }

    @Test
    void bankaCokerseCircuitAcilirSonrakiIslemBankayaGitmezSaglikBildirilir() throws Exception {
        wireMock.stubFor(post(urlPathEqualTo("/banks/QNB/v1/authorize")).willReturn(aResponse().withStatus(503)));
        for (int i = 0; i < 3; i++) {
            String paymentId = UUID.randomUUID().toString();
            publish(paymentId, BankCode.QNB);
            awaitStatus(paymentId, BankTransactionStatus.UNKNOWN);
        }
        assertThat(circuitBreakerRegistry.circuitBreaker("QNB").getState()).isEqualTo(CircuitBreaker.State.OPEN);

        String rejected = UUID.randomUUID().toString();
        publish(rejected, BankCode.QNB);
        assertThat(awaitStatus(rejected, BankTransactionStatus.FAILED).getMessage()).contains("circuit açık");
        wireMock.verify(3, postRequestedFor(urlPathEqualTo("/banks/QNB/v1/authorize")));

        JsonNode health = objectMapper.readTree(consumeLast(BankHealthChangedEvent.TOPIC, "QNB").value());
        assertThat(health.get("healthy").asBoolean()).isFalse();
        assertThat(health.get("circuitState").asText()).isEqualTo("OPEN");

        // YKB etkilenmedi
        assertThat(circuitBreakerRegistry.circuitBreaker("YKB").getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void echoBasariliOlursaCircuitKapanir() {
        CircuitBreaker qnb = circuitBreakerRegistry.circuitBreaker("QNB");
        qnb.transitionToOpenState();
        wireMock.stubFor(get(urlPathEqualTo("/banks/QNB/v1/echo")).willReturn(aResponse().withStatus(200)));

        await().atMost(Duration.ofSeconds(5)).until(() -> qnb.getState() == CircuitBreaker.State.HALF_OPEN);
        probeJob.probe();

        assertThat(qnb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        wireMock.verify(3, getRequestedFor(urlPathEqualTo("/banks/QNB/v1/echo")));
    }

    @Autowired
    private com.bartugsevindik.paymentswitch.bank.job.BankHealthProbeJob probeJob;

    @Autowired
    private BankOperationRepository bankOperationRepository;

    @Test
    void iptalBasariliOlur() throws Exception {
        String paymentId = UUID.randomUUID().toString();
        String operationId = UUID.randomUUID().toString();
        wireMock.stubFor(post(urlPathEqualTo("/banks/YKB/v1/transactions/" + paymentId + "/void"))
                .willReturn(okJson(operationResponse(paymentId, "VOIDED", "00"))));

        publishOperation(operationId, paymentId, BankOperationType.VOID, 125050);

        assertThat(awaitOperation(operationId, BankOperationStatus.SUCCEEDED).getAttempts()).isEqualTo(1);
        outboxRelay.publishPending();
        JsonNode result = objectMapper.readTree(consumeByKey(BankOperationResultEvent.TOPIC, paymentId).value());
        assertThat(result.get("type").asText()).isEqualTo("VOID");
        assertThat(result.get("status").asText()).isEqualTo("SUCCEEDED");
    }

    @Test
    void cevapsizKalanIadeAyniOperationIdIleTekrarDenenir() {
        String paymentId = UUID.randomUUID().toString();
        String operationId = UUID.randomUUID().toString();
        String url = "/banks/YKB/v1/transactions/" + paymentId + "/refunds";
        // İlk deneme timeout, ikinci deneme başarılı
        wireMock.stubFor(post(urlPathEqualTo(url)).inScenario(operationId).whenScenarioStateIs(Scenario.STARTED)
                .willReturn(okJson(operationResponse(paymentId, "REFUNDED", "00")).withFixedDelay(2500))
                .willSetStateTo("second"));
        wireMock.stubFor(post(urlPathEqualTo(url)).inScenario(operationId).whenScenarioStateIs("second")
                .willReturn(okJson(operationResponse(paymentId, "REFUNDED", "00"))));

        publishOperation(operationId, paymentId, BankOperationType.REFUND, 50000);
        await().atMost(Duration.ofSeconds(15)).until(() -> bankOperationRepository.findByOperationId(operationId)
                .map(op -> op.getAttempts() == 1 && op.getNextAttemptAt() != null).orElse(false));

        recoveryService.runOnce();

        assertThat(bankOperationRepository.findByOperationId(operationId).orElseThrow().getStatus())
                .isEqualTo(BankOperationStatus.SUCCEEDED);
        // Satıştan farklı: iade retry edildi, iki istek de aynı operationId ile
        wireMock.verify(2, postRequestedFor(urlPathEqualTo(url))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.containing(operationId)));
    }

    @Test
    void bankaIadeyiReddederseFailedOlur() {
        String paymentId = UUID.randomUUID().toString();
        String operationId = UUID.randomUUID().toString();
        wireMock.stubFor(post(urlPathEqualTo("/banks/YKB/v1/transactions/" + paymentId + "/refunds"))
                .willReturn(okJson(operationResponse(paymentId, "DECLINED", "13"))));

        publishOperation(operationId, paymentId, BankOperationType.REFUND, 999999);

        assertThat(awaitOperation(operationId, BankOperationStatus.FAILED).getResponseCode()).isEqualTo("13");
    }

    private void publishOperation(String operationId, String paymentId, BankOperationType type, long amount) {
        BankOperationRequestedEvent event = new BankOperationRequestedEvent(operationId, paymentId, BankCode.YKB, type,
                amount, "TRY", Instant.now());
        try {
            ProducerRecord<String, String> record = new ProducerRecord<>(BankCode.YKB.requestTopic(), paymentId,
                    objectMapper.writeValueAsString(event));
            record.headers().add(MessageHeaders.EVENT_ID, UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
            record.headers().add(MessageHeaders.EVENT_TYPE, "BankOperationRequestedEvent".getBytes(StandardCharsets.UTF_8));
            kafkaTemplate.send(record).get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private BankOperation awaitOperation(String operationId, BankOperationStatus status) {
        return await().atMost(Duration.ofSeconds(20)).until(
                () -> bankOperationRepository.findByOperationId(operationId).orElse(null),
                op -> op != null && op.getStatus() == status);
    }

    private static String operationResponse(String orderId, String status, String code) {
        return """
                {"orderId": "%s", "status": "%s", "responseCode": "%s", "message": "test"}
                """.formatted(orderId, status, code);
    }

    private void publish(String paymentId, BankCode bank) {
        BankAuthorizationRequestedEvent event = new BankAuthorizationRequestedEvent(paymentId, "MRC0000001", "TRM00000001",
                TerminalType.VIRTUAL, bank, true, "54006170", "2306", UUID.randomUUID().toString(),
                125050, "TRY", 1, "LOWEST_COST", Instant.now());
        try {
            ProducerRecord<String, String> record = new ProducerRecord<>(bank.requestTopic(), paymentId,
                    objectMapper.writeValueAsString(event));
            record.headers().add(MessageHeaders.EVENT_ID, UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
            kafkaTemplate.send(record).get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private BankTransaction awaitStatus(String paymentId, BankTransactionStatus status) {
        return await().atMost(Duration.ofSeconds(20)).until(
                () -> bankTransactionRepository.findByPaymentId(paymentId).orElse(null),
                t -> t != null && t.getStatus() == status);
    }

    private static void stubAuthorize(String orderId, com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder response) {
        wireMock.stubFor(post(urlPathEqualTo("/banks/YKB/v1/authorize"))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.containing(orderId))
                .willReturn(response));
    }

    private static void stubInquiry(String orderId, com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder response) {
        wireMock.stubFor(get(urlPathEqualTo("/banks/YKB/v1/transactions/" + orderId)).willReturn(response));
    }

    private static void stubReversal(String orderId, com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder response) {
        wireMock.stubFor(post(urlPathEqualTo("/banks/YKB/v1/transactions/" + orderId + "/reversal")).willReturn(response));
    }

    private static String approved(String orderId) {
        return """
                {"orderId": "%s", "status": "APPROVED", "responseCode": "00", "authCode": "482915",
                 "rrn": "627104839215", "message": "Onaylandı"}
                """.formatted(orderId);
    }

    private ConsumerRecord<String, String> consumeByKey(String topic, String key) {
        List<ConsumerRecord<String, String>> found = consume(topic, key);
        return found.getFirst();
    }

    private ConsumerRecord<String, String> consumeLast(String topic, String key) {
        List<ConsumerRecord<String, String>> found = consume(topic, key);
        return found.getLast();
    }

    private List<ConsumerRecord<String, String>> consume(String topic, String key) {
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
        return found;
    }
}
