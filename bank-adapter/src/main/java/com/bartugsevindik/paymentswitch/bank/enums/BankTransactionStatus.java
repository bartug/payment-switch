/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.bank.enums;

import com.bartugsevindik.paymentswitch.common.enums.BankResultStatus;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * <h1>BankTransactionStatus</h1>
 * <ul>
 *     <li>{@code SENDING}: bankaya gönderiliyor. Bu durumda takılı kalırsa uygulama cevap beklerken çökmüştür.</li>
 *     <li>{@code UNKNOWN}: cevap alınamadı. Inquiry yapılacak.</li>
 *     <li>{@code REVERSING}: inquiry sonuç vermedi ya da banka işlemi bulamadı; geç ulaşma ihtimaline karşı iptal ediliyor.</li>
 *     <li>{@code MANUAL_REVIEW}: reversal da başarısız. Operasyon müdahalesi gerekir, mutabakatta kontrol edilmeli.</li>
 * </ul>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-5
 */
@Getter
@RequiredArgsConstructor
public enum BankTransactionStatus {
    SENDING(null, false),
    APPROVED(BankResultStatus.APPROVED, true),
    DECLINED(BankResultStatus.DECLINED, true),
    UNKNOWN(BankResultStatus.UNKNOWN, false),
    REVERSING(null, false),
    REVERSED(BankResultStatus.REVERSED, true),
    FAILED(BankResultStatus.FAILED, true),
    MANUAL_REVIEW(null, true);

    /**
     * payment-api'ye bildirilecek sonuç. Boşsa bu duruma geçişte event üretilmez.
     */
    private final BankResultStatus resultStatus;
    private final boolean terminal;
}
