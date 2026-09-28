/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.common.event;

import com.bartugsevindik.paymentswitch.common.enums.BankCode;
import com.bartugsevindik.paymentswitch.common.enums.BankResultStatus;

import java.time.Instant;

/**
 * <h1>BankAuthorizationResultEvent</h1>
 * <p>bank-adapter tarafından {@code payment.bank.results} topic'ine basılır. Aynı ödeme için birden fazla gelebilir:
 * önce {@code UNKNOWN}, inquiry sonrası {@code APPROVED} ya da {@code REVERSED}.</p>
 *
 * @param responseCode ISO 8583 DE39 karşılığı banka cevap kodu (00 onay, 51 yetersiz bakiye...)
 * @param authCode     Onay kodu (DE38). Sadece onaylı işlemde dolu.
 * @param rrn          Banka referans numarası (DE37). Mutabakatta eşleştirme anahtarı.
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-5
 */
public record BankAuthorizationResultEvent(
        String paymentId,
        BankCode bankCode,
        BankResultStatus status,
        String responseCode,
        String authCode,
        String rrn,
        String message,
        Instant occurredAt
) {
    public static final String TOPIC = "payment.bank.results";
}
