/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.ledger.enums;

public enum EntryDirection {
    DEBIT,
    CREDIT;

    public EntryDirection opposite() {
        return this == DEBIT ? CREDIT : DEBIT;
    }
}
