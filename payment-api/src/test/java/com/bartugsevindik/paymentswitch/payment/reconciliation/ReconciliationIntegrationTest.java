/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.reconciliation;

import com.bartugsevindik.paymentswitch.common.enums.BankCode;
import com.bartugsevindik.paymentswitch.common.enums.BankOperationType;
import com.bartugsevindik.paymentswitch.common.enums.BankResultStatus;
import com.bartugsevindik.paymentswitch.common.enums.OperationResultStatus;
import com.bartugsevindik.paymentswitch.common.event.BankAuthorizationResultEvent;
import com.bartugsevindik.paymentswitch.common.event.BankOperationResultEvent;
import com.bartugsevindik.paymentswitch.messaging.MessageHeaders;
import com.bartugsevindik.paymentswitch.payment.enums.PaymentStatus;
import com.bartugsevindik.paymentswitch.payment.reconciliation.dto.ReconciliationItemDTO;
import com.bartugsevindik.paymentswitch.payment.reconciliation.dto.ReconciliationRunDTO;
import com.bartugsevindik.paymentswitch.payment.reconciliation.enums.ReconciliationResult;
import com.bartugsevindik.paymentswitch.payment.reconciliation.service.ReconciliationService;
import com.bartugsevindik.paymentswitch.payment.repository.PaymentRepository;
import com.bartugsevindik.paymentswitch.payment.support.AbstractIntegrationTest;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tüm sonuç tipleri tek bir gün ve tek bir banka (AKBANK) üzerinde kurulur. Diğer testler YKB / QNB kullandığı için
 * onların ödemeleri bu mutabakata karışmaz. Ödeme tutarı 1250,50 TL = 125050 kuruş.
 */
class ReconciliationIntegrationTest extends AbstractIntegrationTest {

    private static final String HEADER = "order_id,type,amount,currency,rrn,auth_code,operation_id,transaction_time\n";

    @RegisterExtension
    static WireMockExtension bank = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    @DynamicPropertySource
    static void bankFileUrl(DynamicPropertyRegistry registry) {
        registry.add("application.reconciliation.bank-file-base-url", bank::baseUrl);
        registry.add("application.payment-operation.business-day-cutoff", () -> "23:59:59");
    }

    @Autowired
    private ReconciliationService reconciliationService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void tumFarkTipleriYakalanir() throws Exception {
        TestTerminal terminal = newTerminal();
        String matched = approved(terminal);
        String amountDiff = approved(terminal);
        String missingInBank = approved(terminal);
        String nextDay = approved(terminal);
        String reversedButCharged = reversed(terminal);
        String refundId = refundSucceeded(terminal, matched, "250.00");
        String bankOnly = UUID.randomUUID().toString();

        LocalDate today = LocalDate.now(ZoneId.of("Europe/Istanbul"));
        stubFile(today.minusDays(1), "");
        stubFile(today, sale(matched, 125050) + sale(amountDiff, 125000) + sale(reversedButCharged, 125050)
                + sale(bankOnly, 50000) + refund(matched, refundId, 25000));
        stubFile(today.plusDays(1), sale(nextDay, 125050));

        ReconciliationRunDTO run = reconciliationService.reconcile(BankCode.AKBANK, today);

        assertThat(run.matched()).isEqualTo(2);
        assertThat(run.matchedOtherDay()).isEqualTo(1);
        assertThat(run.amountMismatch()).isEqualTo(1);
        assertThat(run.missingInBank()).isEqualTo(1);
        assertThat(run.missingInOurs()).isEqualTo(1);
        assertThat(run.statusMismatch()).isEqualTo(1);

        Map<String, ReconciliationItemDTO> byOrder = reconciliationService.getItems(run.runId()).stream()
                .filter(item -> item.recordType().name().equals("SALE"))
                .collect(Collectors.toMap(ReconciliationItemDTO::orderId, item -> item));
        assertThat(byOrder.get(matched).result()).isEqualTo(ReconciliationResult.MATCHED);
        assertThat(byOrder.get(nextDay).result()).isEqualTo(ReconciliationResult.MATCHED_DIFFERENT_DAY);
        assertThat(byOrder.get(amountDiff).result()).isEqualTo(ReconciliationResult.AMOUNT_MISMATCH);
        assertThat(byOrder.get(amountDiff).bankAmount()).isEqualByComparingTo("1250.00");
        assertThat(byOrder.get(missingInBank).result()).isEqualTo(ReconciliationResult.MISSING_IN_BANK);
        assertThat(byOrder.get(bankOnly).result()).isEqualTo(ReconciliationResult.MISSING_IN_OURS);
        // Bizde teknik iptal edildi sanılan işlem bankada onaylı: kart sahibinden para çekilmiş
        assertThat(byOrder.get(reversedButCharged).result()).isEqualTo(ReconciliationResult.STATUS_MISMATCH);
        assertThat(byOrder.get(reversedButCharged).ourStatus()).isEqualTo("REVERSED");

        // Aksiyon gerektirenler özette ayrıca listelenir; eşleşenler listede yok
        assertThat(run.exceptions()).extracting(ReconciliationItemDTO::result).containsExactlyInAnyOrder(
                ReconciliationResult.AMOUNT_MISMATCH, ReconciliationResult.MISSING_IN_BANK,
                ReconciliationResult.MISSING_IN_OURS, ReconciliationResult.STATUS_MISMATCH);
    }

    @Test
    void dosyaYoksaTumOnayliIslemlerBankadaYokSayilir() throws Exception {
        LocalDate date = LocalDate.of(2020, 1, 15);
        bank.stubFor(get(urlPathEqualTo("/banks/GARANTI/v1/settlement-files/" + date)).willReturn(aResponse().withStatus(404)));

        ReconciliationRunDTO run = reconciliationService.reconcile(BankCode.GARANTI, date);

        assertThat(run.matched() + run.missingInBank() + run.missingInOurs()).isZero();
    }

    private void stubFile(LocalDate date, String lines) {
        bank.stubFor(get(urlPathEqualTo("/banks/AKBANK/v1/settlement-files/" + date))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "text/csv").withBody(HEADER + lines)));
    }

    private static String sale(String orderId, long amount) {
        return orderId + ",SALE," + amount + ",TRY,627104839215,482915,," + Instant.now() + "\n";
    }

    private static String refund(String orderId, String operationId, long amount) {
        return orderId + ",REFUND," + amount + ",TRY,627104839215,," + operationId + "," + Instant.now() + "\n";
    }

    private String approved(TestTerminal terminal) throws Exception {
        String paymentId = createPayment(terminal);
        publishBankResult(paymentId, BankResultStatus.APPROVED);
        awaitStatus(paymentId, PaymentStatus.APPROVED);
        return paymentId;
    }

    private String reversed(TestTerminal terminal) throws Exception {
        String paymentId = createPayment(terminal);
        publishBankResult(paymentId, BankResultStatus.UNKNOWN);
        awaitStatus(paymentId, PaymentStatus.UNKNOWN);
        publishBankResult(paymentId, BankResultStatus.REVERSED);
        awaitStatus(paymentId, PaymentStatus.REVERSED);
        return paymentId;
    }

    private String refundSucceeded(TestTerminal terminal, String paymentId, String amount) throws Exception {
        String body = mockMvc.perform(signedPostTo(terminal, "/v1/payments/" + paymentId + "/refunds",
                UUID.randomUUID().toString(), "{\"amount\": " + amount + "}")).andReturn().getResponse().getContentAsString();
        String operationId = objectMapper.readTree(body).at("/object/operationId").asText();
        send(BankOperationResultEvent.TOPIC, paymentId, objectMapper.writeValueAsString(new BankOperationResultEvent(
                operationId, paymentId, BankOperationType.REFUND, OperationResultStatus.SUCCEEDED, "00", "ok", Instant.now())));
        awaitStatus(paymentId, PaymentStatus.PARTIALLY_REFUNDED);
        return operationId;
    }

    private String createPayment(TestTerminal terminal) throws Exception {
        return paymentId(mockMvc.perform(signedPost(terminal, UUID.randomUUID().toString(), VALID_REQUEST))
                .andExpect(status().isAccepted()).andReturn().getResponse());
    }

    private void publishBankResult(String paymentId, BankResultStatus status) throws Exception {
        send(BankAuthorizationResultEvent.TOPIC, paymentId, objectMapper.writeValueAsString(new BankAuthorizationResultEvent(
                paymentId, BankCode.AKBANK, status, status == BankResultStatus.APPROVED ? "00" : null,
                status == BankResultStatus.APPROVED ? "482915" : null, "627104839215", "test", Instant.now())));
    }

    private void send(String topic, String key, String value) throws Exception {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, value);
        record.headers().add(MessageHeaders.EVENT_ID, UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
        kafkaTemplate.send(record).get();
    }

    private void awaitStatus(String paymentId, PaymentStatus status) {
        await().atMost(Duration.ofSeconds(15)).until(
                () -> paymentRepository.findByPaymentId(paymentId).orElseThrow().getPaymentStatus() == status);
    }
}
