/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.ledger;

import com.bartugsevindik.paymentswitch.common.enums.BankCode;
import com.bartugsevindik.paymentswitch.common.enums.BankOperationType;
import com.bartugsevindik.paymentswitch.common.enums.BankResultStatus;
import com.bartugsevindik.paymentswitch.common.enums.OperationResultStatus;
import com.bartugsevindik.paymentswitch.common.event.BankAuthorizationResultEvent;
import com.bartugsevindik.paymentswitch.common.event.BankOperationResultEvent;
import com.bartugsevindik.paymentswitch.messaging.MessageHeaders;
import com.bartugsevindik.paymentswitch.payment.enums.PaymentStatus;
import com.bartugsevindik.paymentswitch.payment.ledger.dto.AccountBalanceDTO;
import com.bartugsevindik.paymentswitch.payment.ledger.dto.JournalEntryDTO;
import com.bartugsevindik.paymentswitch.payment.ledger.dto.JournalLineDTO;
import com.bartugsevindik.paymentswitch.payment.ledger.enums.EntryDirection;
import com.bartugsevindik.paymentswitch.payment.ledger.enums.LedgerEntryType;
import com.bartugsevindik.paymentswitch.payment.ledger.service.LedgerService;
import com.bartugsevindik.paymentswitch.payment.repository.PaymentRepository;
import com.bartugsevindik.paymentswitch.payment.support.AbstractIntegrationTest;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ödeme 1250,50 TL, komisyon %2,49: 125050 × 249 / 10000 = 3113,745 → 3114 kuruş (31,14 TL). Üye işyeri payı 1219,36 TL.
 */
@TestPropertySource(properties = "application.payment-operation.business-day-cutoff=23:59:59")
class LedgerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private LedgerService ledgerService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void satisKaydiKomisyonuAyirirDengede() throws Exception {
        TestTerminal terminal = newTerminal();
        String paymentId = approvedPayment(terminal);

        List<JournalEntryDTO> entries = ledgerService.getEntries(paymentId);
        assertThat(entries).hasSize(1);
        JournalEntryDTO sale = entries.getFirst();
        assertThat(sale.entryType()).isEqualTo(LedgerEntryType.SALE);
        assertThat(sale.lines()).extracting(JournalLineDTO::accountCode, JournalLineDTO::direction, JournalLineDTO::amount)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("BANK_RECEIVABLE:YKB", EntryDirection.DEBIT, new BigDecimal("1250.50")),
                        org.assertj.core.groups.Tuple.tuple("MERCHANT_PAYABLE:" + terminal.merchantId(), EntryDirection.CREDIT, new BigDecimal("1219.36")),
                        org.assertj.core.groups.Tuple.tuple("FEE_REVENUE", EntryDirection.CREDIT, new BigDecimal("31.14")));
        assertBalanced(sale);
    }

    @Test
    void kismiIadeUyeIsyeriAlacagindanDusulurKomisyonKalir() throws Exception {
        TestTerminal terminal = newTerminal();
        String paymentId = approvedPayment(terminal);

        refundSucceeded(terminal, paymentId, "500.00");

        assertThat(balance("MERCHANT_PAYABLE:" + terminal.merchantId())).isEqualByComparingTo("719.36");
        JournalEntryDTO refund = ledgerService.getEntries(paymentId).getLast();
        assertThat(refund.entryType()).isEqualTo(LedgerEntryType.REFUND);
        assertBalanced(refund);
    }

    @Test
    void iptalSatisKaydininTersKaydidirHerSeySifirlanir() throws Exception {
        TestTerminal terminal = newTerminal();
        String paymentId = approvedPayment(terminal);

        String operationId = operationId(mockMvc.perform(signedPostTo(terminal, "/v1/payments/" + paymentId + "/void",
                UUID.randomUUID().toString(), "")).andReturn().getResponse().getContentAsString());
        publishOperationResult(operationId, paymentId, BankOperationType.VOID);
        awaitStatus(paymentId, PaymentStatus.VOIDED);

        List<JournalEntryDTO> entries = ledgerService.getEntries(paymentId);
        assertThat(entries).extracting(JournalEntryDTO::entryType).containsExactly(LedgerEntryType.SALE, LedgerEntryType.VOID);
        // Kayıt silinmedi; ters kayıt atıldı ve üye işyerinin bakiyesi sıfırlandı
        assertThat(balance("MERCHANT_PAYABLE:" + terminal.merchantId())).isEqualByComparingTo("0");
        assertThat(entries.getLast().lines()).extracting(JournalLineDTO::direction)
                .containsExactly(EntryDirection.CREDIT, EntryDirection.DEBIT, EntryDirection.DEBIT);
    }

    @Test
    void mizanDengede() throws Exception {
        approvedPayment(newTerminal());

        assertThat(ledgerService.getTrialBalance().balanced()).isTrue();
    }

    @Test
    void dengesizKayitVeritabaniTarafindanReddedilir() {
        String entryId = UUID.randomUUID().toString();

        // Uygulama katmanını atlayıp doğrudan SQL ile sadece borç satırı yazılıyor
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.update("""
                    INSERT INTO journal_entry (entry_id, payment_id, reference_id, entry_type, currency, created_date)
                    VALUES (?, ?, ?, 'SALE', 'TRY', now())
                    """, entryId, UUID.randomUUID().toString(), UUID.randomUUID().toString());
            jdbcTemplate.update("""
                    INSERT INTO journal_line (entry_id, account_code, account_type, direction, amount, currency, created_date)
                    VALUES (?, 'BANK_RECEIVABLE:YKB', 'ASSET', 'DEBIT', 100000, 'TRY', now())
                    """, entryId);
        })).hasStackTraceContaining("is not balanced");

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM journal_entry WHERE entry_id = ?", Long.class, entryId)).isZero();
    }

    private void assertBalanced(JournalEntryDTO entry) {
        BigDecimal debit = entry.lines().stream().filter(l -> l.direction() == EntryDirection.DEBIT)
                .map(JournalLineDTO::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credit = entry.lines().stream().filter(l -> l.direction() == EntryDirection.CREDIT)
                .map(JournalLineDTO::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(debit).isEqualByComparingTo(credit);
    }

    private BigDecimal balance(String account) {
        return ledgerService.getBalances(account).stream().map(AccountBalanceDTO::balance).findFirst().orElse(BigDecimal.ZERO);
    }

    private String approvedPayment(TestTerminal terminal) throws Exception {
        String paymentId = paymentId(mockMvc.perform(signedPost(terminal, UUID.randomUUID().toString(), VALID_REQUEST))
                .andExpect(status().isAccepted()).andReturn().getResponse());
        send(BankAuthorizationResultEvent.TOPIC, paymentId, objectMapper.writeValueAsString(new BankAuthorizationResultEvent(
                paymentId, BankCode.YKB, BankResultStatus.APPROVED, "00", "482915", "627104839215", "Onaylandı", Instant.now())));
        awaitStatus(paymentId, PaymentStatus.APPROVED);
        return paymentId;
    }

    private void refundSucceeded(TestTerminal terminal, String paymentId, String amount) throws Exception {
        String operationId = operationId(mockMvc.perform(signedPostTo(terminal, "/v1/payments/" + paymentId + "/refunds",
                UUID.randomUUID().toString(), "{\"amount\": " + amount + "}")).andReturn().getResponse().getContentAsString());
        publishOperationResult(operationId, paymentId, BankOperationType.REFUND);
        awaitStatus(paymentId, PaymentStatus.PARTIALLY_REFUNDED);
    }

    private void publishOperationResult(String operationId, String paymentId, BankOperationType type) throws Exception {
        send(BankOperationResultEvent.TOPIC, paymentId, objectMapper.writeValueAsString(new BankOperationResultEvent(
                operationId, paymentId, type, OperationResultStatus.SUCCEEDED, "00", "ok", Instant.now())));
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

    private String operationId(String body) throws Exception {
        return objectMapper.readTree(body).at("/object/operationId").asText();
    }
}
