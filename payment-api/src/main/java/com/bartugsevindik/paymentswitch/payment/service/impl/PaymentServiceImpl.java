/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.service.impl;

import com.bartugsevindik.paymentswitch.common.enums.TerminalType;
import com.bartugsevindik.paymentswitch.common.event.PaymentRequestedEvent;
import com.bartugsevindik.paymentswitch.common.exception.BadRequestException;
import com.bartugsevindik.paymentswitch.common.exception.NotFoundException;
import com.bartugsevindik.paymentswitch.payment.dto.PaymentCreateRequest;
import com.bartugsevindik.paymentswitch.payment.dto.PaymentDTO;
import com.bartugsevindik.paymentswitch.payment.entity.Payment;
import com.bartugsevindik.paymentswitch.payment.enums.PaymentStatus;
import com.bartugsevindik.paymentswitch.payment.idempotency.dto.IdempotencyContext;
import com.bartugsevindik.paymentswitch.payment.idempotency.dto.IdempotentResult;
import com.bartugsevindik.paymentswitch.payment.idempotency.exception.IdempotencyInProgressException;
import com.bartugsevindik.paymentswitch.payment.idempotency.lock.IdempotencyLock;
import com.bartugsevindik.paymentswitch.payment.idempotency.lock.LockResult;
import com.bartugsevindik.paymentswitch.payment.idempotency.service.IdempotencyService;
import com.bartugsevindik.paymentswitch.payment.mapper.PaymentMapper;
import com.bartugsevindik.paymentswitch.payment.outbox.service.OutboxService;
import com.bartugsevindik.paymentswitch.payment.repository.PaymentRepository;
import com.bartugsevindik.paymentswitch.payment.service.PaymentService;
import com.bartugsevindik.paymentswitch.payment.terminal.security.TerminalPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private static final String RESOURCE_TYPE = "PAYMENT";

    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;
    private final IdempotencyService idempotencyService;
    private final IdempotencyLock idempotencyLock;
    private final OutboxService outboxService;
    private final TransactionTemplate transactionTemplate;

    /**
     * <h1>Ödeme Oluşturma</h1>
     * <p>Ödemeyi {@code PENDING} durumunda kaydeder. Bankaya gönderim asenkron yapılır,
     * sonuç {@link #getPayment(TerminalPrincipal, String)} ile sorgulanır.</p>
     * <p>Akış: önceki kayıt var mı → Redis kilidi → (payment + idempotency kaydı + outbox event tek transaction) → kilidi bırak.
     * Transaction bilinçli olarak metodun tamamını kapsamaz; kilit beklenirken DB connection tutulmaz.</p>
     *
     * @param terminal       İmzası doğrulanmış terminal
     * @param idempotencyKey Client'ın ödeme denemesi başına ürettiği key
     * @param request        POS'tan gelen ödeme isteği
     * @return Oluşturulan ya da daha önce oluşmuş ödeme
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-1
     */
    @Override
    public IdempotentResult<PaymentDTO> createPayment(@NotNull TerminalPrincipal terminal, @NotNull String idempotencyKey,
                                                      @NotNull PaymentCreateRequest request) {
        validateCardData(terminal, request);
        IdempotencyContext context = idempotencyService.createContext(terminal.merchantId(), idempotencyKey, request);

        Optional<PaymentDTO> replay = findReplay(context);
        if (replay.isPresent()) {
            return IdempotentResult.replayed(replay.get());
        }

        String lockToken = UUID.randomUUID().toString();
        LockResult lock = idempotencyLock.tryAcquire(context.lockKey(), lockToken);
        if (!lock.canProceed()) {
            // Kilit alınamadı ama ilk istek biz bakana kadar bitmiş olabilir
            return findReplay(context)
                    .map(IdempotentResult::replayed)
                    .orElseThrow(() -> new IdempotencyInProgressException(idempotencyKey));
        }

        try {
            PaymentDTO created = transactionTemplate.execute(status -> persistPayment(terminal, context, request));
            return IdempotentResult.created(created);
        } catch (DataIntegrityViolationException e) {
            // Redis kilidi kaçırdı (fail-open ya da TTL doldu); unique constraint ikinci kaydı engelledi
            log.warn("Idempotency unique constraint hit, replaying. merchantId={}, key={}",
                    context.merchantId(), idempotencyKey);
            return findReplay(context)
                    .map(IdempotentResult::replayed)
                    .orElseThrow(() -> e);
        } finally {
            if (lock == LockResult.ACQUIRED) {
                idempotencyLock.release(context.lockKey(), lockToken);
            }
        }
    }

    /**
     * <h1>Ödeme Getirme</h1>
     * <p>Ödeme ID'si ile ödemenin güncel durumunu döndürür. Terminal sadece kendi üye işyerinin ödemelerini görebilir.</p>
     *
     * @param terminal  İmzası doğrulanmış terminal
     * @param paymentId Ödeme ID
     * @return Ödeme bilgisi
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-1
     */
    @Override
    @Transactional(readOnly = true)
    public PaymentDTO getPayment(@NotNull TerminalPrincipal terminal, @NotNull String paymentId) {
        return paymentRepository.findByPaymentIdAndMerchantId(paymentId, terminal.merchantId())
                .map(paymentMapper::toDto)
                .orElseThrow(() -> new NotFoundException("Ödeme", "paymentId", paymentId));
    }

    private PaymentDTO persistPayment(TerminalPrincipal terminal, IdempotencyContext context, PaymentCreateRequest request) {
        Payment payment = paymentMapper.toEntity(request);
        payment.setPaymentId(UUID.randomUUID().toString());
        payment.setMerchantId(terminal.merchantId());
        payment.setTerminalId(terminal.terminalId());
        payment.setTerminalType(terminal.terminalType());
        payment.changeStatus(PaymentStatus.PENDING);

        Payment saved = paymentRepository.save(payment);
        idempotencyService.saveRecord(context, RESOURCE_TYPE, saved.getPaymentId());
        // Kafka'ya doğrudan yazılmaz; ödeme commit olmadan event gitmez, event gitmeden ödeme kaybolmaz
        outboxService.enqueue(RESOURCE_TYPE, saved.getPaymentId(), PaymentRequestedEvent.TOPIC, toRequestedEvent(saved));
        log.info("Payment created. paymentId={}, merchantId={}, amount={}, installment={}",
                saved.getPaymentId(), saved.getMerchantId(), saved.getMoney(), saved.getInstallmentCount());

        return paymentMapper.toDto(saved);
    }

    private PaymentRequestedEvent toRequestedEvent(Payment payment) {
        return new PaymentRequestedEvent(
                payment.getPaymentId(),
                payment.getMerchantId(),
                payment.getTerminalId(),
                payment.getTerminalType(),
                payment.getCardBin(),
                payment.getCardLast4(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getInstallmentCount(),
                Instant.now());
    }

    /**
     * Tekrar eden istekte ilk cevabın kopyası değil, ödemenin <b>güncel</b> durumu döner.
     * POS ilk cevabı hiç alamadıysa bile ödemenin bankadan onay alıp almadığını görebilir.
     */
    private Optional<PaymentDTO> findReplay(IdempotencyContext context) {
        return idempotencyService.findExistingResourceId(context)
                .flatMap(paymentRepository::findByPaymentId)
                .map(paymentMapper::toDto);
    }

    /**
     * Sanal POS'ta kart fiziken olmadığı için CVV zorunludur. Fiziki POS'ta çip verisi kullanılır.
     */
    private void validateCardData(TerminalPrincipal terminal, PaymentCreateRequest request) {
        if (terminal.terminalType() == TerminalType.VIRTUAL && request.getCvv() == null) {
            throw new BadRequestException("Sanal POS işlemlerinde CVV zorunludur.");
        }
    }
}
