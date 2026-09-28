/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.routing.enums;

import com.bartugsevindik.paymentswitch.common.enums.BankCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * <h1>CardProgram</h1>
 * <p>Taksit programları. Taksitli işlem, programın sahibi bankanın POS'undan geçmek zorundadır.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-4
 */
@Getter
@RequiredArgsConstructor
public enum CardProgram {
    WORLD(BankCode.YKB),
    BONUS(BankCode.GARANTI),
    AXESS(BankCode.AKBANK),
    CARDFINANS(BankCode.QNB),
    MAXIMUM(BankCode.ISBANK);

    private final BankCode programBank;
}
