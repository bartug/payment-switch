/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.bank.service.impl;

import com.bartugsevindik.paymentswitch.bank.client.BankApiClient;
import com.bartugsevindik.paymentswitch.bank.client.BankCallException;
import com.bartugsevindik.paymentswitch.bank.client.BankClientErrorException;
import com.bartugsevindik.paymentswitch.bank.config.BankAdapterProperties;
import com.bartugsevindik.paymentswitch.bank.dto.BankApiResponse;
import com.bartugsevindik.paymentswitch.bank.entity.BankOperation;
import com.bartugsevindik.paymentswitch.bank.enums.BankOperationStatus;
import com.bartugsevindik.paymentswitch.bank.repository.BankOperationRepository;
import com.bartugsevindik.paymentswitch.bank.service.BankOperationService;
import com.bartugsevindik.paymentswitch.common.enums.BankOperationType;
import com.bartugsevindik.paymentswitch.common.enums.OperationResultStatus;
import com.bartugsevindik.paymentswitch.common.event.BankOperationRequestedEvent;
import com.bartugsevindik.paymentswitch.common.event.BankOperationResultEvent;
import com.bartugsevindik.paymentswitch.messaging.consumer.IncomingEvent;
import com.bartugsevindik.paymentswitch.messaging.inbox.InboxService;
import com.bartugsevindik.paymentswitch.messaging.outbox.OutboxService;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BankOperationServiceImpl implements BankOperationService {

    private static final String AGGREGATE_TYPE = "PAYMENT";
    private static final Duration CLAIM_LEASE = Duration.ofSeconds(30);
    private static final Duration MAX_BACKOFF = Duration.ofSeconds(60);
    private static final int MAX_ATTEMPTS = 10;

    private final BankOperationRepository bankOperationRepository;
    private final BankApiClient bankApiClient;
    private final InboxService inboxService;
    private final OutboxService outboxService;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final TransactionTemplate transactionTemplate;
    private final BankAdapterProperties properties;
    private final MeterRegistry meterRegistry;

    /**
     * <h1>İşlemi Bankaya Gönderme</h1>
     *
     * @param event {@code bank.requests.{BANKA}} topic'inden gelen iptal / iade isteği
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 29.09.2026 - PS-6
     */
    @Override
    public void process(@NotNull IncomingEvent<BankOperationRequestedEvent> event) {
        BankOperationRequestedEvent request = event.payload();
        Optional<BankOperation> claimed = transactionTemplate.execute(status -> {
            if (!inboxService.markProcessed(event.eventId(), BankTransactionServiceImpl.CONSUMER)
                    || bankOperationRepository.existsByOperationId(request.operationId())) {
                return Optional.<BankOperation>empty();
            }
            return Optional.of(bankOperationRepository.save(BankOperation.builder()
                    .operationId(request.operationId())
                    .paymentId(request.paymentId())
                    .orderId(request.paymentId())
                    .bankCode(request.bankCode())
                    .type(request.type())
                    .amount(request.amount())
                    .currency(request.currency())
                    .status(BankOperationStatus.PENDING)
                    .attempts(0)
                    .build()));
        });
        claimed.ifPresent(this::attempt);
    }

    /**
     * <h1>Bekleyen İşlemleri Tekrar Deneme</h1>
     *
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 29.09.2026 - PS-6
     */
    @Override
    public void retryDue() {
        List<BankOperation> due = transactionTemplate.execute(status -> {
            List<BankOperation> locked = bankOperationRepository.lockDue(LocalDateTime.now(), properties.getRecovery().getBatchSize());
            locked.forEach(operation -> operation.setNextAttemptAt(LocalDateTime.now().plus(CLAIM_LEASE)));
            return locked;
        });
        for (BankOperation operation : due) {
            try {
                attempt(operation);
            } catch (RuntimeException e) {
                log.error("Bank operation retry failed unexpectedly. operationId={}", operation.getOperationId(), e);
            }
        }
    }

    /**
     * Banka çağrısı transaction dışında. Circuit açıksa banka hiç çağrılmaz, işlem sonra tekrar denenir.
     */
    private void attempt(BankOperation operation) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(operation.getBankCode().name());
        try {
            BankApiResponse response = circuitBreaker.executeSupplier(() -> operation.getType() == BankOperationType.VOID
                    ? bankApiClient.voidTransaction(operation.getBankCode(), operation.getOrderId(), operation.getOperationId())
                    : bankApiClient.refund(operation.getBankCode(), operation.getOrderId(), operation.getOperationId(), operation.getAmount()));
            boolean succeeded = "VOIDED".equals(response.status()) || "REFUNDED".equals(response.status());
            complete(operation.getId(), succeeded ? OperationResultStatus.SUCCEEDED : OperationResultStatus.FAILED,
                    response.responseCode(), response.message());
        } catch (BankClientErrorException e) {
            complete(operation.getId(), OperationResultStatus.FAILED, null, "Banka isteği reddetti.");
        } catch (CallNotPermittedException | BankCallException e) {
            scheduleRetry(operation.getId(), e.getMessage());
        }
    }

    private void complete(Long id, OperationResultStatus status, String responseCode, String message) {
        transactionTemplate.executeWithoutResult(tx -> {
            BankOperation operation = bankOperationRepository.findById(id).orElseThrow();
            if (operation.getStatus() != BankOperationStatus.PENDING) {
                return;
            }
            operation.setStatus(status == OperationResultStatus.SUCCEEDED ? BankOperationStatus.SUCCEEDED : BankOperationStatus.FAILED);
            operation.setResponseCode(responseCode);
            operation.setMessage(message);
            operation.setAttempts(operation.getAttempts() + 1);
            operation.setNextAttemptAt(null);
            outboxService.enqueue(AGGREGATE_TYPE, operation.getPaymentId(), BankOperationResultEvent.TOPIC,
                    new BankOperationResultEvent(operation.getOperationId(), operation.getPaymentId(), operation.getType(),
                            status, responseCode, message, Instant.now()));
            meterRegistry.counter("bank.operations", "bank", operation.getBankCode().name(),
                    "type", operation.getType().name(), "status", operation.getStatus().name()).increment();
            log.info("Bank operation completed. operationId={}, paymentId={}, type={}, status={}, responseCode={}",
                    operation.getOperationId(), operation.getPaymentId(), operation.getType(), operation.getStatus(), responseCode);
        });
    }

    private void scheduleRetry(Long id, String error) {
        transactionTemplate.executeWithoutResult(tx -> {
            BankOperation operation = bankOperationRepository.findById(id).orElseThrow();
            int attempts = operation.getAttempts() + 1;
            operation.setAttempts(attempts);
            operation.setLastError(error == null ? null : error.substring(0, Math.min(error.length(), 512)));
            if (attempts >= MAX_ATTEMPTS) {
                operation.setStatus(BankOperationStatus.MANUAL_REVIEW);
                operation.setNextAttemptAt(null);
                meterRegistry.counter("bank.operations.manual.review", "bank", operation.getBankCode().name()).increment();
                log.error("Bank operation failed permanently, MANUAL REVIEW required. operationId={}, paymentId={}, type={}, error={}",
                        operation.getOperationId(), operation.getPaymentId(), operation.getType(), error);
                return;
            }
            Duration delay = properties.getRecovery().getInquiryBackoff().multipliedBy(1L << Math.min(attempts - 1, 10));
            operation.setNextAttemptAt(LocalDateTime.now().plus(delay.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : delay));
            log.warn("Bank operation will be retried with same operationId. operationId={}, attempt={}, cause={}",
                    operation.getOperationId(), attempts, error);
        });
    }
}
