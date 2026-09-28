/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.terminal.security;

import com.bartugsevindik.paymentswitch.common.enums.TerminalType;

/**
 * <h1>TerminalPrincipal</h1>
 * <p>İmzası doğrulanmış terminal. Filter tarafından request attribute olarak eklenir.
 * Üye işyeri bilgisi body'den değil buradan alınır; terminal başka bir üye işyeri adına işlem yapamaz.</p>
 *
 * @param terminalId   Terminal numarası
 * @param merchantId   Terminalin bağlı olduğu üye işyeri
 * @param terminalType Terminal tipi
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-2
 */
public record TerminalPrincipal(String terminalId, String merchantId, TerminalType terminalType) {

    public static final String REQUEST_ATTRIBUTE = "authenticatedTerminal";
}
