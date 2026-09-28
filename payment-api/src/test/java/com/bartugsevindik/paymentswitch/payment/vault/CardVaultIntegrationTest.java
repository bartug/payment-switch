/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.vault;

import com.bartugsevindik.paymentswitch.common.enums.BankCode;
import com.bartugsevindik.paymentswitch.common.enums.BankResultStatus;
import com.bartugsevindik.paymentswitch.common.event.BankAuthorizationResultEvent;
import com.bartugsevindik.paymentswitch.messaging.MessageHeaders;
import com.bartugsevindik.paymentswitch.messaging.outbox.OutboxEventRepository;
import com.bartugsevindik.paymentswitch.payment.enums.PaymentStatus;
import com.bartugsevindik.paymentswitch.payment.repository.PaymentRepository;
import com.bartugsevindik.paymentswitch.payment.support.AbstractIntegrationTest;
import com.bartugsevindik.paymentswitch.payment.vault.config.CardVaultProperties;
import com.bartugsevindik.paymentswitch.payment.vault.entity.CardVaultEntry;
import com.bartugsevindik.paymentswitch.payment.vault.repository.CardVaultRepository;
import com.bartugsevindik.paymentswitch.payment.vault.security.InternalApiTokenFilter;
import com.fasterxml.jackson.databind.JsonNode;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CardVaultIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private CardVaultRepository cardVaultRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private CardVaultProperties cardVaultProperties;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void kartVerisiSifreliSaklanirEventteSadeceTokenGider() throws Exception {
        String paymentId = createPayment();

        CardVaultEntry entry = cardVaultRepository.findAll().stream()
                .filter(e -> e.getPaymentId().equals(paymentId)).findFirst().orElseThrow();
        assertThat(entry.getCardDataCiphertext()).startsWith("v1:").doesNotContain("5400617020092306");

        JsonNode event = objectMapper.readTree(outboxEventRepository.findFirstByAggregateIdOrderByIdDesc(paymentId)
                .orElseThrow().getPayload());
        assertThat(event.get("cardToken").asText()).isEqualTo(entry.getToken());
        assertThat(event.toString()).doesNotContain("5400617020092306").doesNotContain("cvv");
    }

    @Test
    void internalTokenOlmadanKartVerisiAlinamaz() throws Exception {
        String token = tokenOf(createPayment());

        mockMvc.perform(post("/internal/v1/card-vault/{token}/detokenize", token))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/internal/v1/card-vault/{token}/detokenize", token)
                        .header(InternalApiTokenFilter.INTERNAL_TOKEN_HEADER, "yanlis"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void internalTokenIleKartVerisiCozulur() throws Exception {
        String token = tokenOf(createPayment());

        mockMvc.perform(detokenize(token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.object.pan").value("5400617020092306"))
                .andExpect(jsonPath("$.object.cvv").value("000"));
    }

    @Test
    void bankaSonucuGelinceKartVerisiSilinirOdemeGuncellenir() throws Exception {
        String paymentId = createPayment();
        String token = tokenOf(paymentId);

        publishBankResult(paymentId, BankResultStatus.APPROVED, "00", "482915");

        await().atMost(Duration.ofSeconds(15)).until(() ->
                paymentRepository.findByPaymentId(paymentId).orElseThrow().getPaymentStatus() == PaymentStatus.APPROVED);
        assertThat(paymentRepository.findByPaymentId(paymentId).orElseThrow().getAuthCode()).isEqualTo("482915");
        assertThat(cardVaultRepository.findAll().stream().noneMatch(e -> e.getPaymentId().equals(paymentId))).isTrue();
        // CVV yetkilendirmeden sonra hiçbir yerde durmamalı (PCI DSS)
        mockMvc.perform(detokenize(token)).andExpect(status().isNotFound());
    }

    @Test
    void cevapsizIslemOnceUnknownSonraInquiryIleApprovedOlur() throws Exception {
        String paymentId = createPayment();

        publishBankResult(paymentId, BankResultStatus.UNKNOWN, null, null);
        await().atMost(Duration.ofSeconds(15)).until(() ->
                paymentRepository.findByPaymentId(paymentId).orElseThrow().getPaymentStatus() == PaymentStatus.UNKNOWN);

        publishBankResult(paymentId, BankResultStatus.APPROVED, "00", "111222");
        await().atMost(Duration.ofSeconds(15)).until(() ->
                paymentRepository.findByPaymentId(paymentId).orElseThrow().getPaymentStatus() == PaymentStatus.APPROVED);
    }

    @Test
    void onaylanmisOdemeyeGecikmisUnknownGelirseGeriAlinmaz() throws Exception {
        String paymentId = createPayment();
        publishBankResult(paymentId, BankResultStatus.APPROVED, "00", "333444");
        await().atMost(Duration.ofSeconds(15)).until(() ->
                paymentRepository.findByPaymentId(paymentId).orElseThrow().getPaymentStatus() == PaymentStatus.APPROVED);

        publishBankResult(paymentId, BankResultStatus.UNKNOWN, null, null);
        String marker = createPayment();
        publishBankResult(marker, BankResultStatus.DECLINED, "51", null);
        await().atMost(Duration.ofSeconds(15)).until(() ->
                paymentRepository.findByPaymentId(marker).orElseThrow().getPaymentStatus() == PaymentStatus.DECLINED);

        assertThat(paymentRepository.findByPaymentId(paymentId).orElseThrow().getPaymentStatus()).isEqualTo(PaymentStatus.APPROVED);
    }

    private String createPayment() throws Exception {
        return paymentId(mockMvc.perform(signedPost(newTerminal(), UUID.randomUUID().toString(), VALID_REQUEST))
                .andExpect(status().isAccepted()).andReturn().getResponse());
    }

    private String tokenOf(String paymentId) {
        return cardVaultRepository.findAll().stream().filter(e -> e.getPaymentId().equals(paymentId))
                .findFirst().orElseThrow().getToken();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder detokenize(String token) {
        return post("/internal/v1/card-vault/{token}/detokenize", token)
                .header(InternalApiTokenFilter.INTERNAL_TOKEN_HEADER, cardVaultProperties.getInternalApiToken());
    }

    private void publishBankResult(String paymentId, BankResultStatus status, String responseCode, String authCode) throws Exception {
        BankAuthorizationResultEvent event = new BankAuthorizationResultEvent(paymentId, BankCode.YKB, status, responseCode,
                authCode, "627104839215", "test", Instant.now());
        ProducerRecord<String, String> record = new ProducerRecord<>(BankAuthorizationResultEvent.TOPIC, paymentId,
                objectMapper.writeValueAsString(event));
        record.headers().add(MessageHeaders.EVENT_ID, UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
        kafkaTemplate.send(record).get();
    }
}
