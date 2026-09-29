/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.common.enums;

/**
 * <h1>BankOperationType</h1>
 * <p>{@code VOID}: gün sonu öncesi iptal, işlem takasa girmez. {@code REFUND}: gün sonu sonrası iade, kısmi olabilir.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 29.09.2026 - PS-6
 */
public enum BankOperationType {
    VOID,
    REFUND
}
