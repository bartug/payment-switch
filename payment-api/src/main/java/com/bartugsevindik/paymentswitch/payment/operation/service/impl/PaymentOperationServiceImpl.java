/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.operation.service.impl;

import com.bartugsevindik.paymentswitch.common.enums.BankOperationType;
import com.bartugsevindik.paymentswitch.common.enums.OperationResultStatus;
import com.bartugsevindik.paymentswitch.common.event.BankOperationRequestedEvent;
import com.bartugsevindik.paymentswitch.common.event.BankOperationResultEvent;
import com.bartugsevindik.paymentswitch.common.exception.ConflictException;
import com.bartugsevindik.paymentswitch.common.exception.NotFoundException;
import com.bartugsevindik.paymentswitch.common.exception.UnprocessableEntityException;
import com.bartugsevindik.paymentswitch.common.model.Money;
import com.bartugsevindik.paymentswitch.messaging.consumer.IncomingEvent;
import com.bartugsevindik.paymentswitch.messaging.inbox.InboxService;
import com.bartugsevindik.paymentswitch.messaging.outbox.OutboxService;
import com.bartugsevindik.paymentswitch.payment.entity.Payment;
import com.bartugsevindik.paymentswitch.payment.enums.PaymentStatus;
import com.bartugsevindik.paymentswitch.payment.idempotency.dto.IdempotencyContext;
import com.bartugsevindik.paymentswitch.payment.idempotency.dto.IdempotentResult;
import com.bartugsevindik.paymentswitch.payment.idempotency.service.IdempotencyService;
import com.bartugsevindik.paymentswitch.payment.operation.config.PaymentOperationProperties;
import com.bartugsevindik.paymentswitch.payment.operation.dto.PaymentOperationDTO;
import com.bartugsevindik.paymentswitch.payment.operation.dto.RefundRequest;
import com.bartugsevindik.paymentswitch.payment.operation.entity.PaymentOperation;
import com.bartugsevindik.paymentswitch.payment.operation.enums.PaymentOperationStatus;
import com.bartugsevindik.paymentswitch.payment.operation.repository.PaymentOperationRepository;
import com.bartugsevindik.paymentswitch.payment.operation.service.PaymentOperationService;
import com.bartugsevindik.paymentswitch.payment.repository.PaymentRepository;
import com.bartugsevindik.paymentswitch.payment.terminal.security.TerminalPrincipal;
import com.bartugsevindik.paymentswitch.payment.webhook.service.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentOperationServiceImpl implements PaymentOperationService {

    private static final String RESOURCE_TYPE = "PAYMENT_OPERATION";
    private static final String AGGREGATE_TYPE = "PAYMENT";
    private static final String CONSUMER = "payment-api";

    private final PaymentRepository paymentRepository;
    private final PaymentOperationRepository paymentOperationRepository;
    private final IdempotencyService idempotencyService;
    private final OutboxService outboxService;
    private final InboxService inboxService;
    private final WebhookService webhookService;
    private final PaymentOperationProperties properties;
    private final TransactionTemplate transactionTemplate;

    /**
     * İdempotency hash'i için istek. Aynı key ile farklı ödeme / tutar gelirse 422 döner.
     */
    private record OperationRequest(String paymentId, BankOperationType type, BigDecimal amount) {
    }

    /**
     * <h1>İptal Talebi</h1>
     * <p>Sadece aynı iş günü, gün sonu saatinden önce ve hiç iade yapılmamış onaylı ödeme iptal edilebilir.
     * Gün sonundan sonra işlem takasa girmiştir; iade yapılmalıdır.</p>
     *
     * @param terminal       İmzası doğrulanmış terminal
     * @param paymentId      Ödeme ID
     * @param idempotencyKey Tekrar gönderimde aynı key
     * @return Oluşan ya da daha önce oluşmuş iptal işlemi
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 29.09.2026 - PS-6
     */
    @Override
    public IdempotentResult<PaymentOperationDTO> requestVoid(@NotNull TerminalPrincipal terminal, @NotNull String paymentId,
                                                             @NotNull String idempotencyKey) {
        return execute(terminal, paymentId, idempotencyKey, BankOperationType.VOID, null);
    }

    /**
     * <h1>İade Talebi</h1>
     * <p>Kısmi iade yapılabilir. Yeni iade + başarılı iadeler + sonucu beklenen iadeler ödeme tutarını aşamaz.
     * Ödeme satırı kilitlenir; aynı anda gelen iki iade sırayla değerlendirilir.</p>
     *
     * @param terminal       İmzası doğrulanmış terminal
     * @param paymentId      Ödeme ID
     * @param idempotencyKey Tekrar gönderimde aynı key
     * @param request        İade tutarı
     * @return Oluşan ya da daha önce oluşmuş iade işlemi
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 29.09.2026 - PS-6
     */
    @Override
    public IdempotentResult<PaymentOperationDTO> requestRefund(@NotNull TerminalPrincipal terminal, @NotNull String paymentId,
                                                               @NotNull String idempotencyKey, @NotNull RefundRequest request) {
        return execute(terminal, paymentId, idempotencyKey, BankOperationType.REFUND, request.getAmount());
    }

    /**
     * <h1>İşlemleri Listeleme</h1>
     *
     * @param terminal  İmzası doğrulanmış terminal
     * @param paymentId Ödeme ID
     * @return Ödemenin iptal / iade işlemleri
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 29.09.2026 - PS-6
     */
    @Override
    @Transactional(readOnly = true)
    public List<PaymentOperationDTO> getOperations(@NotNull TerminalPrincipal terminal, @NotNull String paymentId) {
        paymentRepository.findByPaymentIdAndMerchantId(paymentId, terminal.merchantId())
                .orElseThrow(() -> new NotFoundException("Ödeme", "paymentId", paymentId));
        return paymentOperationRepository.findByPaymentIdOrderByIdAsc(paymentId).stream()
                .map(PaymentOperationServiceImpl::toDto).toList();
    }

    /**
     * <h1>Banka Sonucunu İşleme</h1>
     * <p>İptal başarılıysa ödeme {@code VOIDED}, başarısızsa tekrar {@code APPROVED} olur. İade başarılıysa iade
     * edilen tutar artar ve ödeme {@code PARTIALLY_REFUNDED} ya da {@code REFUNDED} olur.</p>
     *
     * @param event {@code payment.bank.operation-results} topic'inden gelen sonuç
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 29.09.2026 - PS-6
     */
    @Override
    @Transactional
    public void applyResult(@NotNull IncomingEvent<BankOperationResultEvent> event) {
        BankOperationResultEvent result = event.payload();
        if (!inboxService.markProcessed(event.eventId(), CONSUMER)) {
            return;
        }
        PaymentOperation operation = paymentOperationRepository.findByOperationId(result.operationId())
                .orElseThrow(() -> new NotFoundException("İşlem", "operationId", result.operationId()));
        if (operation.getStatus() != PaymentOperationStatus.PENDING) {
            return;
        }
        Payment payment = paymentRepository.findForUpdate(operation.getPaymentId()).orElseThrow();
        boolean succeeded = result.status() == OperationResultStatus.SUCCEEDED;

        operation.setStatus(succeeded ? PaymentOperationStatus.SUCCEEDED : PaymentOperationStatus.FAILED);
        operation.setResponseCode(result.responseCode());
        operation.setFailureReason(succeeded ? null : result.message());

        if (operation.getType() == BankOperationType.VOID) {
            payment.changeStatus(succeeded ? PaymentStatus.VOIDED : PaymentStatus.APPROVED);
        } else if (succeeded) {
            payment.setRefundedAmount(payment.getRefundedAmount() + operation.getAmount());
            payment.changeStatus(payment.getRefundedAmount().equals(payment.getAmount())
                    ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED);
        }
        if (succeeded) {
            webhookService.enqueue(payment);
        }
        log.info("Payment operation result applied. operationId={}, paymentId={}, type={}, status={}, paymentStatus={}",
                operation.getOperationId(), payment.getPaymentId(), operation.getType(), operation.getStatus(),
                payment.getPaymentStatus());
    }

    private IdempotentResult<PaymentOperationDTO> execute(TerminalPrincipal terminal, String paymentId, String idempotencyKey,
                                                          BankOperationType type, BigDecimal amount) {
        IdempotencyContext context = idempotencyService.createContext(terminal.merchantId(), idempotencyKey,
                new OperationRequest(paymentId, type, amount));
        Optional<PaymentOperationDTO> replay = findReplay(context);
        if (replay.isPresent()) {
            return IdempotentResult.replayed(replay.get());
        }
        try {
            return transactionTemplate.execute(status -> create(terminal, paymentId, context, type, amount));
        } catch (DataIntegrityViolationException e) {
            return findReplay(context).map(IdempotentResult::replayed).orElseThrow(() -> e);
        }
    }

    private IdempotentResult<PaymentOperationDTO> create(TerminalPrincipal terminal, String paymentId, IdempotencyContext context,
                                                         BankOperationType type, BigDecimal amount) {
        // Satır kilidi: aynı ödemeye eş zamanlı gelen iptal / iade istekleri burada sıraya girer
        Payment payment = paymentRepository.findForUpdate(paymentId, terminal.merchantId())
                .orElseThrow(() -> new NotFoundException("Ödeme", "paymentId", paymentId));
        // Kilidi bekleyen aynı key'li istek, ilk istek commit olduktan sonra kaydı burada görür
        Optional<PaymentOperationDTO> replay = findReplay(context);
        if (replay.isPresent()) {
            return IdempotentResult.replayed(replay.get());
        }

        long operationAmount = type == BankOperationType.VOID ? payment.getAmount() : Money.of(amount, payment.getCurrency()).amount();
        if (type == BankOperationType.VOID) {
            validateVoid(payment);
        } else {
            validateRefund(payment, operationAmount);
        }

        PaymentOperation operation = paymentOperationRepository.save(PaymentOperation.builder()
                .operationId(UUID.randomUUID().toString())
                .paymentId(paymentId)
                .merchantId(payment.getMerchantId())
                .type(type)
                .amount(operationAmount)
                .currency(payment.getCurrency())
                .status(PaymentOperationStatus.PENDING)
                .build());
        if (type == BankOperationType.VOID) {
            payment.changeStatus(PaymentStatus.VOIDING);
        }
        idempotencyService.saveRecord(context, RESOURCE_TYPE, operation.getOperationId());
        // Satışla aynı banka topic'i ve aynı key: aynı partition, satıştan önce işlenemez
        outboxService.enqueue(AGGREGATE_TYPE, paymentId, payment.getBankCode().requestTopic(),
                new BankOperationRequestedEvent(operation.getOperationId(), paymentId, payment.getBankCode(), type,
                        operationAmount, payment.getCurrency(), Instant.now()));
        log.info("Payment operation requested. operationId={}, paymentId={}, type={}, amount={}",
                operation.getOperationId(), paymentId, type, Money.ofMinor(operationAmount, payment.getCurrency()));
        return IdempotentResult.created(toDto(operation));
    }

    private void validateVoid(Payment payment) {
        if (payment.getPaymentStatus() == PaymentStatus.VOIDING) {
            throw new ConflictException("Ödeme için iptal işlemi devam ediyor.");
        }
        if (payment.getPaymentStatus() != PaymentStatus.APPROVED) {
            throw new UnprocessableEntityException("Sadece onaylı ödemeler iptal edilebilir. Durum: " + payment.getPaymentStatus());
        }
        if (payment.getRefundedAmount() > 0
                || paymentOperationRepository.existsByPaymentIdAndStatus(payment.getPaymentId(), PaymentOperationStatus.PENDING)) {
            throw new UnprocessableEntityException("İade yapılmış ya da iadesi devam eden ödeme iptal edilemez.");
        }
        ZoneId zone = properties.getBusinessZone();
        ZonedDateTime now = ZonedDateTime.now(zone);
        ZonedDateTime createdAt = payment.getCreatedDate().atZone(ZoneId.systemDefault()).withZoneSameInstant(zone);
        if (!createdAt.toLocalDate().equals(now.toLocalDate()) || !now.toLocalTime().isBefore(properties.getBusinessDayCutoff())) {
            throw new UnprocessableEntityException("Gün sonu geçmiş ödeme iptal edilemez, iade yapılmalıdır.");
        }
    }

    private void validateRefund(Payment payment, long amount) {
        if (payment.getPaymentStatus() == PaymentStatus.VOIDING) {
            throw new ConflictException("Ödeme için iptal işlemi devam ediyor.");
        }
        if (!EnumSet.of(PaymentStatus.APPROVED, PaymentStatus.PARTIALLY_REFUNDED).contains(payment.getPaymentStatus())) {
            throw new UnprocessableEntityException("Sadece onaylı ödemeler iade edilebilir. Durum: " + payment.getPaymentStatus());
        }
        long pending = paymentOperationRepository.sumAmount(payment.getPaymentId(), BankOperationType.REFUND, PaymentOperationStatus.PENDING);
        long refundable = payment.getAmount() - payment.getRefundedAmount() - pending;
        if (amount > refundable) {
            throw new UnprocessableEntityException("İade tutarı iade edilebilir tutarı aşıyor. İade edilebilir: "
                    + Money.ofMinor(refundable, payment.getCurrency()));
        }
    }

    private Optional<PaymentOperationDTO> findReplay(IdempotencyContext context) {
        return idempotencyService.findExistingResourceId(context)
                .flatMap(paymentOperationRepository::findByOperationId)
                .map(PaymentOperationServiceImpl::toDto);
    }

    private static PaymentOperationDTO toDto(PaymentOperation o) {
        return new PaymentOperationDTO(o.getOperationId(), o.getPaymentId(), o.getType(),
                Money.ofMinor(o.getAmount(), o.getCurrency()).toDecimal(), o.getCurrency(), o.getStatus(),
                o.getResponseCode(), o.getFailureReason(), o.getCreatedDate());
    }
}
