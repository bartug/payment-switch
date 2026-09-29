/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.operation.service;

import com.bartugsevindik.paymentswitch.common.event.BankOperationResultEvent;
import com.bartugsevindik.paymentswitch.messaging.consumer.IncomingEvent;
import com.bartugsevindik.paymentswitch.payment.idempotency.dto.IdempotentResult;
import com.bartugsevindik.paymentswitch.payment.operation.dto.PaymentOperationDTO;
import com.bartugsevindik.paymentswitch.payment.operation.dto.RefundRequest;
import com.bartugsevindik.paymentswitch.payment.terminal.security.TerminalPrincipal;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <h1>PaymentOperationService</h1>
 * <p>Onaylı ödemelerin iptali (void) ve iadesi (refund).</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 29.09.2026 - PS-6
 */
@Service
public interface PaymentOperationService {

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
    IdempotentResult<PaymentOperationDTO> requestVoid(TerminalPrincipal terminal, String paymentId, String idempotencyKey);

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
    IdempotentResult<PaymentOperationDTO> requestRefund(TerminalPrincipal terminal, String paymentId, String idempotencyKey,
                                                        RefundRequest request);

    /**
     * <h1>İşlemleri Listeleme</h1>
     *
     * @param terminal  İmzası doğrulanmış terminal
     * @param paymentId Ödeme ID
     * @return Ödemenin iptal / iade işlemleri
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 29.09.2026 - PS-6
     */
    List<PaymentOperationDTO> getOperations(TerminalPrincipal terminal, String paymentId);

    /**
     * <h1>Banka Sonucunu İşleme</h1>
     * <p>İptal başarılıysa ödeme {@code VOIDED}, başarısızsa tekrar {@code APPROVED} olur. İade başarılıysa iade
     * edilen tutar artar ve ödeme {@code PARTIALLY_REFUNDED} ya da {@code REFUNDED} olur.</p>
     *
     * @param event {@code payment.bank.operation-results} topic'inden gelen sonuç
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 29.09.2026 - PS-6
     */
    void applyResult(IncomingEvent<BankOperationResultEvent> event);
}
