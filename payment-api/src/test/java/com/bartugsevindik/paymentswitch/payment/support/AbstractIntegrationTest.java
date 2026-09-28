/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.support;

import com.bartugsevindik.paymentswitch.common.enums.TerminalType;
import com.bartugsevindik.paymentswitch.payment.controller.PaymentController;
import com.bartugsevindik.paymentswitch.payment.outbox.relay.OutboxRelay;
import com.bartugsevindik.paymentswitch.payment.terminal.dto.TerminalCreateRequest;
import com.bartugsevindik.paymentswitch.payment.terminal.dto.TerminalDTO;
import com.bartugsevindik.paymentswitch.payment.terminal.security.RequestSigner;
import com.bartugsevindik.paymentswitch.payment.terminal.security.TerminalAuthenticationFilter;
import com.bartugsevindik.paymentswitch.payment.terminal.service.TerminalService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Container'lar tüm test sınıfları için bir kez açılır (singleton container pattern).
 * Her sınıfta yeniden açılsaydı Spring context cache'i de işe yaramazdı.
 */
@SpringBootTest(properties = "application.scheduling.enabled=false")
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    // JVM'siz (GraalVM native) imaj; hızlı açılır, Apple M4'teki JDK SVE sorunundan etkilenmez
    @ServiceConnection
    protected static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka-native:3.8.1");

    static {
        POSTGRES.start();
        REDIS.start();
        KAFKA.start();
    }

    protected static final String VALID_REQUEST = """
            {
              "amount": 1250.50,
              "currency": "TRY",
              "installmentCount": 3,
              "cardNumber": "5400617020092306",
              "expiryMonth": "12",
              "expiryYear": "28",
              "cvv": "000"
            }
            """;

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected TerminalService terminalService;

    @Autowired
    protected OutboxRelay outboxRelay;

    public record TestTerminal(String terminalId, String merchantId, String secret) {
    }

    /**
     * Her testin kendi terminali ve üye işyeri olur; testler birbirinin verisinden etkilenmez.
     */
    protected TestTerminal newTerminal(TerminalType type) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        TerminalDTO dto = terminalService.createTerminal(TerminalCreateRequest.builder()
                .terminalId("TRM" + suffix)
                .merchantId("MRC" + suffix)
                .terminalType(type)
                .build());
        return new TestTerminal(dto.getTerminalId(), dto.getMerchantId(), dto.getSecret());
    }

    protected TestTerminal newTerminal() {
        return newTerminal(TerminalType.VIRTUAL);
    }

    protected MockHttpServletRequestBuilder signedPost(TestTerminal terminal, String idempotencyKey, String body) {
        return signedPost(terminal, idempotencyKey, body, Instant.now().getEpochSecond());
    }

    protected MockHttpServletRequestBuilder signedPost(TestTerminal terminal, String idempotencyKey, String body, long timestamp) {
        String path = "/v1/payments";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        String signature = RequestSigner.sign(terminal.secret(),
                RequestSigner.stringToSign("POST", path, String.valueOf(timestamp), idempotencyKey, bytes));
        MockHttpServletRequestBuilder builder = post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bytes)
                .header(TerminalAuthenticationFilter.TERMINAL_ID_HEADER, terminal.terminalId())
                .header(TerminalAuthenticationFilter.TIMESTAMP_HEADER, String.valueOf(timestamp))
                .header(TerminalAuthenticationFilter.SIGNATURE_HEADER, signature);
        return idempotencyKey == null ? builder : builder.header(PaymentController.IDEMPOTENCY_KEY_HEADER, idempotencyKey);
    }

    protected MockHttpServletRequestBuilder signedGet(TestTerminal terminal, String path) {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String signature = RequestSigner.sign(terminal.secret(),
                RequestSigner.stringToSign("GET", path, timestamp, null, new byte[0]));
        return get(path)
                .header(TerminalAuthenticationFilter.TERMINAL_ID_HEADER, terminal.terminalId())
                .header(TerminalAuthenticationFilter.TIMESTAMP_HEADER, timestamp)
                .header(TerminalAuthenticationFilter.SIGNATURE_HEADER, signature);
    }

    protected String paymentId(MockHttpServletResponse response) {
        try {
            return objectMapper.readTree(response.getContentAsString()).at("/object/paymentId").asText();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
