/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.bank.enums;

/**
 * <h1>BankOperationStatus</h1>
 * <p>{@code MANUAL_REVIEW}: tekrar denemeler bitti, banka hâlâ cevap vermiyor. İade yapılıp yapılmadığı bilinmiyor;
 * operasyon bankayla teyit etmeli.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 29.09.2026 - PS-6
 */
public enum BankOperationStatus {
    PENDING,
    SUCCEEDED,
    FAILED,
    MANUAL_REVIEW
}
