/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.service;

import com.bartugsevindik.paymentswitch.common.event.BankAuthorizationResultEvent;
import com.bartugsevindik.paymentswitch.common.event.PaymentRoutingResultEvent;
import com.bartugsevindik.paymentswitch.messaging.consumer.IncomingEvent;
import com.bartugsevindik.paymentswitch.payment.dto.PaymentCreateRequest;
import com.bartugsevindik.paymentswitch.payment.dto.PaymentDTO;
import com.bartugsevindik.paymentswitch.payment.idempotency.dto.IdempotentResult;
import com.bartugsevindik.paymentswitch.payment.terminal.security.TerminalPrincipal;
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
     * sonuç {@link #getPayment(TerminalPrincipal, String)} ile sorgulanır.</p>
     * <p>Aynı Idempotency-Key ile tekrar gelen istekte yeni ödeme oluşmaz, mevcut ödemenin güncel hali döner.</p>
     *
     * @param terminal       İmzası doğrulanmış terminal
     * @param idempotencyKey Client'ın ödeme denemesi başına ürettiği key
     * @param request        POS'tan gelen ödeme isteği
     * @return Oluşturulan ya da daha önce oluşmuş ödeme
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-1
     */
    IdempotentResult<PaymentDTO> createPayment(TerminalPrincipal terminal, String idempotencyKey, PaymentCreateRequest request);

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
    PaymentDTO getPayment(TerminalPrincipal terminal, String paymentId);

    /**
     * <h1>Routing Sonucunu İşleme</h1>
     * <p>routing-service'in kararına göre ödemeyi {@code ROUTED} ya da {@code FAILED} durumuna çeker.
     * Aynı event ikinci kez gelirse hiçbir şey yapılmaz.</p>
     *
     * @param event {@code payment.routing.results} topic'inden gelen event
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-4
     */
    void applyRoutingResult(IncomingEvent<PaymentRoutingResultEvent> event);

    /**
     * <h1>Banka Sonucunu İşleme</h1>
     * <p>bank-adapter'ın bildirdiği sonuca göre ödemenin durumunu günceller ve kart verisini siler.</p>
     *
     * @param event {@code payment.bank.results} topic'inden gelen event
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-5
     */
    void applyBankResult(IncomingEvent<BankAuthorizationResultEvent> event);
}
