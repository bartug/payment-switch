/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.service;

import com.bartugsevindik.paymentswitch.payment.dto.PaymentCreateRequest;
import com.bartugsevindik.paymentswitch.payment.dto.PaymentDTO;
import com.bartugsevindik.paymentswitch.payment.idempotency.dto.IdempotentResult;
import org.springframework.stereotype.Service;

/**
 * <h1>PaymentService</h1>
 * <p>POS'tan gelen ödemelerin karşılanması ve sorgulanması.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-1
 */
@Service
public interface PaymentService {

    /**
     * <h1>Ödeme Oluşturma</h1>
     * <p>Ödemeyi {@code PENDING} durumunda kaydeder. Bankaya gönderim asenkron yapılır,
     * sonuç {@link #getPayment(String)} ile sorgulanır.</p>
     * <p>Aynı Idempotency-Key ile tekrar gelen istekte yeni ödeme oluşmaz, mevcut ödemenin güncel hali döner.</p>
     *
     * @param idempotencyKey Client'ın ödeme denemesi başına ürettiği key
     * @param request        POS'tan gelen ödeme isteği
     * @return Oluşturulan ya da daha önce oluşmuş ödeme
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-1
     */
    IdempotentResult<PaymentDTO> createPayment(String idempotencyKey, PaymentCreateRequest request);

    /**
     * <h1>Ödeme Getirme</h1>
     * <p>Ödeme ID'si ile ödemenin güncel durumunu döndürür.</p>
     *
     * @param paymentId Ödeme ID
     * @return Ödeme bilgisi
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-1
     */
    PaymentDTO getPayment(String paymentId);
}
