/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.terminal.enums;

/**
 * <h1>TerminalStatus</h1>
 * <p>{@code PASSIVE}: terminal kapatılmış ya da çalıntı bildirilmiş. İmzası doğru olsa bile işlem alamaz.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-2
 */
public enum TerminalStatus {
    ACTIVE,
    PASSIVE
}
