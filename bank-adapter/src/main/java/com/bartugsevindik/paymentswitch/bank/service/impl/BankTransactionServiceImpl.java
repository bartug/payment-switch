/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.bank.service.impl;

import com.bartugsevindik.paymentswitch.bank.config.BankAdapterProperties;
import com.bartugsevindik.paymentswitch.bank.dto.BankOutcome;
import com.bartugsevindik.paymentswitch.bank.entity.BankTransaction;
import com.bartugsevindik.paymentswitch.bank.enums.BankTransactionStatus;
import com.bartugsevindik.paymentswitch.bank.repository.BankTransactionRepository;
import com.bartugsevindik.paymentswitch.bank.service.BankTransactionService;
import com.bartugsevindik.paymentswitch.common.event.BankAuthorizationRequestedEvent;
import com.bartugsevindik.paymentswitch.common.event.BankAuthorizationResultEvent;
import com.bartugsevindik.paymentswitch.messaging.inbox.InboxService;
import com.bartugsevindik.paymentswitch.messaging.outbox.OutboxService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BankTransactionServiceImpl implements BankTransactionService {

    public static final String CONSUMER = "bank-adapter";
    private static final String AGGREGATE_TYPE = "PAYMENT";
    private static final Duration CLAIM_LEASE = Duration.ofSeconds(30);
    private static final Duration MAX_BACKOFF = Duration.ofSeconds(60);

    private final BankTransactionRepository bankTransactionRepository;
    private final InboxService inboxService;
    private final OutboxService outboxService;
    private final BankAdapterProperties properties;
    private final MeterRegistry meterRegistry;

    /**
     * <h1>İşlemi Sahiplenme</h1>
     * <p>Inbox kaydı ile birlikte işlemi {@code SENDING} olarak yazar. Aynı event ya da aynı ödeme daha önce
     * işlendiyse boş döner ve bankaya gidilmez.</p>
     *
     * @param eventId Kafka event ID
     * @param request Banka isteği
     * @return Sahiplenilen işlem
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-5
     */
    @Override
    @Transactional
    public Optional<BankTransaction> claim(@NotNull String eventId, @NotNull BankAuthorizationRequestedEvent request) {
        if (!inboxService.markProcessed(eventId, CONSUMER)) {
            return Optional.empty();
        }
        if (bankTransactionRepository.existsByPaymentId(request.paymentId())) {
            log.warn("Payment already sent to bank, skipping. paymentId={}, eventId={}", request.paymentId(), eventId);
            return Optional.empty();
        }
        return Optional.of(bankTransactionRepository.save(newTransaction(request, BankTransactionStatus.SENDING)));
    }

    /**
     * <h1>Gönderilmeden Başarısız</h1>
     * <p>İstek bankaya hiç gönderilmeden başarısız olduysa (kart verisi yok) işlemi doğrudan {@code FAILED} yazar.</p>
     *
     * @param eventId Kafka event ID
     * @param request Banka isteği
     * @param reason  Sebep
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-5
     */
    @Override
    @Transactional
    public void recordNotSent(@NotNull String eventId, @NotNull BankAuthorizationRequestedEvent request, @NotNull String reason) {
        if (!inboxService.markProcessed(eventId, CONSUMER) || bankTransactionRepository.existsByPaymentId(request.paymentId())) {
            return;
        }
        BankTransaction transaction = bankTransactionRepository.save(newTransaction(request, BankTransactionStatus.SENDING));
        apply(transaction, BankOutcome.notSent(reason));
    }

    /**
     * <h1>Sonucu Kaydetme</h1>
     * <p>İşlemin durumunu günceller ve payment-api'ye gidecek sonucu outbox'a yazar. Sonuçlanmış işleme dokunmaz.</p>
     *
     * @param transactionId İşlem ID
     * @param outcome       Banka sonucu
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-5
     */
    @Override
    @Transactional
    public void complete(@NotNull Long transactionId, @NotNull BankOutcome outcome) {
        BankTransaction transaction = bankTransactionRepository.findById(transactionId).orElseThrow();
        if (transaction.getStatus().isTerminal()) {
            log.warn("Transaction already finalized, outcome ignored. paymentId={}, status={}, outcome={}",
                    transaction.getPaymentId(), transaction.getStatus(), outcome.status());
            return;
        }
        apply(transaction, outcome);
    }

    /**
     * <h1>Takılı İşlemleri Kurtarma</h1>
     * <p>{@code SENDING}'de takılı işlemleri {@code UNKNOWN} yapar. Uygulama banka cevabını beklerken ölmüştür;
     * istek bankaya gitmiş olabilir.</p>
     *
     * @return Kurtarılan işlem sayısı
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-5
     */
    @Override
    @Transactional
    public int recoverStuckSending() {
        List<BankTransaction> stuck = bankTransactionRepository.lockStuckSending(
                LocalDateTime.now().minus(properties.getRecovery().getStuckAfter()), properties.getRecovery().getBatchSize());
        for (BankTransaction transaction : stuck) {
            log.warn("Stuck SENDING transaction recovered as UNKNOWN. paymentId={}", transaction.getPaymentId());
            apply(transaction, BankOutcome.unknown("Banka cevabı beklenirken uygulama yeniden başladı."));
        }
        return stuck.size();
    }

    /**
     * <h1>Zamanı Gelen İşleri Alma</h1>
     * <p>Inquiry ya da reversal zamanı gelmiş işlemleri kilitler ve bir süre başka pod'ların almaması için ileri erteler.</p>
     *
     * @return İşlenecek işlemler
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-5
     */
    @Override
    @Transactional
    public List<BankTransaction> claimDue() {
        List<BankTransaction> due = bankTransactionRepository.lockDue(LocalDateTime.now(), properties.getRecovery().getBatchSize());
        // Kilit commit ile düşer; banka çağrısı transaction dışında yapılacağı için kayıt süreli olarak ertelenir (lease)
        due.forEach(transaction -> transaction.setNextAttemptAt(LocalDateTime.now().plus(CLAIM_LEASE)));
        return due;
    }

    @Override
    @Transactional
    public void startReversal(@NotNull Long transactionId, @NotNull String reason) {
        BankTransaction transaction = bankTransactionRepository.findById(transactionId).orElseThrow();
        if (transaction.getStatus().isTerminal()) {
            return;
        }
        log.warn("Starting reversal. paymentId={}, reason={}", transaction.getPaymentId(), reason);
        transaction.setStatus(BankTransactionStatus.REVERSING);
        transaction.setAttempts(0);
        transaction.setLastError(reason);
        transaction.setNextAttemptAt(LocalDateTime.now());
    }

    @Override
    @Transactional
    public void inquiryFailed(@NotNull Long transactionId, @NotNull String error) {
        BankTransaction transaction = bankTransactionRepository.findById(transactionId).orElseThrow();
        transaction.recordError(error);
        if (transaction.getAttempts() >= properties.getRecovery().getMaxInquiryAttempts()) {
            startReversal(transactionId, "Inquiry " + transaction.getAttempts() + " denemede sonuç vermedi: " + error);
            return;
        }
        transaction.setNextAttemptAt(LocalDateTime.now().plus(backoff(transaction.getAttempts())));
    }

    @Override
    @Transactional
    public void reversalFailed(@NotNull Long transactionId, @NotNull String error) {
        BankTransaction transaction = bankTransactionRepository.findById(transactionId).orElseThrow();
        transaction.recordError(error);
        if (transaction.getAttempts() >= properties.getRecovery().getMaxReversalAttempts()) {
            transaction.setStatus(BankTransactionStatus.MANUAL_REVIEW);
            transaction.setNextAttemptAt(null);
            meterRegistry.counter("bank.transactions.manual.review", "bank", transaction.getBankCode().name()).increment();
            log.error("Reversal failed permanently, MANUAL REVIEW required. paymentId={}, bank={}, orderId={}, error={}",
                    transaction.getPaymentId(), transaction.getBankCode(), transaction.getOrderId(), error);
            return;
        }
        transaction.setNextAttemptAt(LocalDateTime.now().plus(backoff(transaction.getAttempts())));
    }

    private void apply(BankTransaction transaction, BankOutcome outcome) {
        transaction.setStatus(outcome.status());
        transaction.setResponseCode(outcome.responseCode());
        transaction.setAuthCode(outcome.authCode());
        transaction.setRrn(outcome.rrn() != null ? outcome.rrn() : transaction.getRrn());
        transaction.setMessage(outcome.message());
        if (outcome.status() == BankTransactionStatus.UNKNOWN) {
            transaction.setAttempts(0);
            transaction.setNextAttemptAt(LocalDateTime.now().plus(properties.getRecovery().getInquiryInitialDelay()));
        } else {
            transaction.setNextAttemptAt(null);
        }

        if (outcome.status().getResultStatus() != null) {
            outboxService.enqueue(AGGREGATE_TYPE, transaction.getPaymentId(), BankAuthorizationResultEvent.TOPIC,
                    new BankAuthorizationResultEvent(transaction.getPaymentId(), transaction.getBankCode(),
                            outcome.status().getResultStatus(), outcome.responseCode(), outcome.authCode(),
                            transaction.getRrn(), outcome.message(), Instant.now()));
        }
        meterRegistry.counter("bank.transactions", "bank", transaction.getBankCode().name(),
                "status", outcome.status().name()).increment();
        log.info("Bank transaction updated. paymentId={}, bank={}, status={}, responseCode={}",
                transaction.getPaymentId(), transaction.getBankCode(), outcome.status(), outcome.responseCode());
    }

    private Duration backoff(int attempts) {
        Duration delay = properties.getRecovery().getInquiryBackoff().multipliedBy(1L << Math.min(attempts - 1, 10));
        return delay.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : delay;
    }

    private static BankTransaction newTransaction(BankAuthorizationRequestedEvent request, BankTransactionStatus status) {
        return BankTransaction.builder()
                .paymentId(request.paymentId())
                .orderId(request.paymentId())
                .bankCode(request.bankCode())
                .status(status)
                .amount(request.amount())
                .currency(request.currency())
                .installmentCount(request.installmentCount())
                .attempts(0)
                .build();
    }
}
