/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.service.impl;

import com.bartugsevindik.paymentswitch.common.enums.TerminalType;
import com.bartugsevindik.paymentswitch.common.exception.BadRequestException;
import com.bartugsevindik.paymentswitch.common.exception.NotFoundException;
import com.bartugsevindik.paymentswitch.payment.dto.PaymentCreateRequest;
import com.bartugsevindik.paymentswitch.payment.dto.PaymentDTO;
import com.bartugsevindik.paymentswitch.payment.entity.Payment;
import com.bartugsevindik.paymentswitch.payment.enums.PaymentStatus;
import com.bartugsevindik.paymentswitch.payment.mapper.PaymentMapper;
import com.bartugsevindik.paymentswitch.payment.repository.PaymentRepository;
import com.bartugsevindik.paymentswitch.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;

    /**
     * <h1>Ödeme Oluşturma</h1>
     * <p>Ödemeyi {@code PENDING} durumunda kaydeder. Bankaya gönderim asenkron yapılır,
     * sonuç {@link #getPayment(String)} ile sorgulanır.</p>
     *
     * @param request POS'tan gelen ödeme isteği
     * @return Oluşturulan ödeme
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-1
     */
    @Override
    @Transactional
    public PaymentDTO createPayment(@NotNull PaymentCreateRequest request) {
        validateCardData(request);

        Payment payment = paymentMapper.toEntity(request);
        payment.setPaymentId(UUID.randomUUID().toString());
        payment.changeStatus(PaymentStatus.PENDING);

        Payment saved = paymentRepository.save(payment);
        log.info("Payment created. paymentId={}, merchantId={}, amount={}, installment={}",
                saved.getPaymentId(), saved.getMerchantId(), saved.getMoney(), saved.getInstallmentCount());

        // TODO: PS-3 ile outbox kaydı aynı transaction içinde atılacak
        return paymentMapper.toDto(saved);
    }

    /**
     * <h1>Ödeme Getirme</h1>
     * <p>Ödeme ID'si ile ödemenin güncel durumunu döndürür.</p>
     *
     * @param paymentId Ödeme ID
     * @return Ödeme bilgisi
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-1
     */
    @Override
    @Transactional(readOnly = true)
    public PaymentDTO getPayment(@NotNull String paymentId) {
        return paymentRepository.findByPaymentId(paymentId)
                .map(paymentMapper::toDto)
                .orElseThrow(() -> new NotFoundException("Ödeme", "paymentId", paymentId));
    }

    /**
     * Sanal POS'ta kart fiziken olmadığı için CVV zorunludur. Fiziki POS'ta çip verisi kullanılır.
     */
    private void validateCardData(PaymentCreateRequest request) {
        if (request.getTerminalType() == TerminalType.VIRTUAL && request.getCvv() == null) {
            throw new BadRequestException("Sanal POS işlemlerinde CVV zorunludur.");
        }
    }
}
