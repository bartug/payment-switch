/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.webhook;

import com.bartugsevindik.paymentswitch.common.enums.BankCode;
import com.bartugsevindik.paymentswitch.common.enums.BankResultStatus;
import com.bartugsevindik.paymentswitch.common.event.BankAuthorizationResultEvent;
import com.bartugsevindik.paymentswitch.messaging.MessageHeaders;
import com.bartugsevindik.paymentswitch.payment.enums.PaymentStatus;
import com.bartugsevindik.paymentswitch.payment.merchant.dto.MerchantCreateRequest;
import com.bartugsevindik.paymentswitch.payment.merchant.service.MerchantService;
import com.bartugsevindik.paymentswitch.payment.repository.PaymentRepository;
import com.bartugsevindik.paymentswitch.payment.support.AbstractIntegrationTest;
import com.bartugsevindik.paymentswitch.payment.webhook.entity.WebhookDelivery;
import com.bartugsevindik.paymentswitch.payment.webhook.enums.WebhookDeliveryStatus;
import com.bartugsevindik.paymentswitch.payment.webhook.job.WebhookDispatcher;
import com.bartugsevindik.paymentswitch.payment.webhook.repository.WebhookDeliveryRepository;
import com.bartugsevindik.paymentswitch.payment.webhook.service.WebhookSigner;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.TestPropertySource;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = "application.webhook.retry-schedule=0s,0s")
class WebhookIntegrationTest extends AbstractIntegrationTest {

    @RegisterExtension
    static WireMockExtension merchantServer = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    @Autowired
    private MerchantService merchantService;

    @Autowired
    private WebhookDeliveryRepository webhookDeliveryRepository;

    @Autowired
    private WebhookDispatcher webhookDispatcher;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @BeforeEach
    void reset() {
        merchantServer.resetAll();
    }

    @Test
    void onaylananOdemeImzaliBildirimleUyeIsyerineGider() throws Exception {
        merchantServer.stubFor(post(urlPathEqualTo("/hooks")).willReturn(aResponse().withStatus(200)));
        TestTerminal terminal = newTerminal();
        String secret = registerMerchant(terminal.merchantId());
        String paymentId = createApprovedPayment(terminal);

        webhookDispatcher.dispatchDue();

        List<LoggedRequest> requests = merchantServer.findAll(postRequestedFor(urlPathEqualTo("/hooks")));
        assertThat(requests).hasSize(1);
        LoggedRequest request = requests.getFirst();
        String body = request.getBodyAsString();

        // Üye işyerinin yapacağı doğrulama: t ve v1'i ayır, t + "." + body'yi kendi secret'ı ile imzala, karşılaştır
        String header = request.getHeader(WebhookSigner.SIGNATURE_HEADER);
        long timestamp = Long.parseLong(header.split(",")[0].substring(2));
        String signature = header.split(",")[1].substring(3);
        assertThat(signature).isEqualTo(WebhookSigner.hmacHex(secret, timestamp + "." + body));
        assertThat(Math.abs(Instant.now().getEpochSecond() - timestamp)).isLessThan(60);

        JsonNode payload = objectMapper.readTree(body);
        assertThat(payload.get("type").asText()).isEqualTo("payment.approved");
        assertThat(payload.get("id").asText()).isEqualTo(request.getHeader(WebhookSigner.DELIVERY_ID_HEADER));
        assertThat(payload.at("/data/paymentId").asText()).isEqualTo(paymentId);
        assertThat(payload.at("/data/authCode").asText()).isEqualTo("482915");
        assertThat(body).doesNotContain("5400617020092306");

        WebhookDelivery delivery = webhookDeliveryRepository.findByPaymentIdOrderByIdAsc(paymentId).getFirst();
        assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.DELIVERED);
        assertThat(delivery.getLastStatusCode()).isEqualTo(200);
    }

    @Test
    void uyeIsyeriHataDonerseTekrarDenenirDenemelerBitinceFailedOlur() throws Exception {
        merchantServer.stubFor(post(urlPathEqualTo("/hooks")).willReturn(aResponse().withStatus(503)));
        TestTerminal terminal = newTerminal();
        registerMerchant(terminal.merchantId());
        String paymentId = createApprovedPayment(terminal);

        webhookDispatcher.dispatchDue();
        WebhookDelivery afterFirst = webhookDeliveryRepository.findByPaymentIdOrderByIdAsc(paymentId).getFirst();
        assertThat(afterFirst.getStatus()).isEqualTo(WebhookDeliveryStatus.PENDING);
        assertThat(afterFirst.getAttempts()).isEqualTo(1);
        assertThat(afterFirst.getLastStatusCode()).isEqualTo(503);

        webhookDispatcher.dispatchDue();
        webhookDispatcher.dispatchDue();

        WebhookDelivery afterAll = webhookDeliveryRepository.findByPaymentIdOrderByIdAsc(paymentId).getFirst();
        assertThat(afterAll.getStatus()).isEqualTo(WebhookDeliveryStatus.FAILED);
        assertThat(afterAll.getAttempts()).isEqualTo(3);
        // Her denemede aynı bildirim ID'si gider; üye işyeri tekrarları ayıklayabilir
        assertThat(merchantServer.findAll(postRequestedFor(urlPathEqualTo("/hooks"))))
                .extracting(r -> r.getHeader(WebhookSigner.DELIVERY_ID_HEADER))
                .containsOnly(afterAll.getDeliveryId());
    }

    @Test
    void basarisizBildirimTekrarGonderilebilir() throws Exception {
        merchantServer.stubFor(post(urlPathEqualTo("/hooks")).willReturn(aResponse().withStatus(500)));
        TestTerminal terminal = newTerminal();
        registerMerchant(terminal.merchantId());
        String paymentId = createApprovedPayment(terminal);
        for (int i = 0; i < 3; i++) {
            webhookDispatcher.dispatchDue();
        }
        String deliveryId = webhookDeliveryRepository.findByPaymentIdOrderByIdAsc(paymentId).getFirst().getDeliveryId();

        merchantServer.stubFor(post(urlPathEqualTo("/hooks")).willReturn(aResponse().withStatus(204)));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/v1/admin/webhooks/{deliveryId}/redeliver", deliveryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.object.status").value("PENDING"));
        webhookDispatcher.dispatchDue();

        assertThat(webhookDeliveryRepository.findByDeliveryId(deliveryId).orElseThrow().getStatus())
                .isEqualTo(WebhookDeliveryStatus.DELIVERED);
    }

    @Test
    void webhookTanimliDegilseBildirimOlusmaz() throws Exception {
        String paymentId = createApprovedPayment(newTerminal());

        assertThat(webhookDeliveryRepository.findByPaymentIdOrderByIdAsc(paymentId)).isEmpty();
    }

    @Test
    void araDurumlarBildirilmez() throws Exception {
        TestTerminal terminal = newTerminal();
        registerMerchant(terminal.merchantId());
        String paymentId = createPayment(terminal);

        publishBankResult(paymentId, BankResultStatus.UNKNOWN, null);
        await().atMost(Duration.ofSeconds(15)).until(() ->
                paymentRepository.findByPaymentId(paymentId).orElseThrow().getPaymentStatus() == PaymentStatus.UNKNOWN);

        assertThat(webhookDeliveryRepository.findByPaymentIdOrderByIdAsc(paymentId)).isEmpty();
    }

    private String registerMerchant(String merchantId) {
        return merchantService.createMerchant(MerchantCreateRequest.builder()
                .merchantId(merchantId)
                .name("Test İşyeri")
                .webhookUrl(merchantServer.baseUrl() + "/hooks")
                .build()).getWebhookSecret();
    }

    private String createPayment(TestTerminal terminal) throws Exception {
        return paymentId(mockMvc.perform(signedPost(terminal, UUID.randomUUID().toString(), VALID_REQUEST))
                .andExpect(status().isAccepted()).andReturn().getResponse());
    }

    private String createApprovedPayment(TestTerminal terminal) throws Exception {
        String paymentId = createPayment(terminal);
        publishBankResult(paymentId, BankResultStatus.APPROVED, "482915");
        await().atMost(Duration.ofSeconds(15)).until(() ->
                paymentRepository.findByPaymentId(paymentId).orElseThrow().getPaymentStatus() == PaymentStatus.APPROVED);
        return paymentId;
    }

    private void publishBankResult(String paymentId, BankResultStatus status, String authCode) throws Exception {
        BankAuthorizationResultEvent event = new BankAuthorizationResultEvent(paymentId, BankCode.YKB, status,
                status == BankResultStatus.APPROVED ? "00" : null, authCode, "627104839215", "test", Instant.now());
        ProducerRecord<String, String> record = new ProducerRecord<>(BankAuthorizationResultEvent.TOPIC, paymentId,
                objectMapper.writeValueAsString(event));
        record.headers().add(MessageHeaders.EVENT_ID, UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
        kafkaTemplate.send(record).get();
    }
}
