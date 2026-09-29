/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.ledger.enums;

/**
 * <h1>AccountType</h1>
 * <p>Varlık hesapları borç (debit) ile artar; borç (liability) ve gelir hesapları alacak (credit) ile artar.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 29.09.2026 - PS-7
 */
public enum AccountType {
    ASSET,
    LIABILITY,
    REVENUE
}
