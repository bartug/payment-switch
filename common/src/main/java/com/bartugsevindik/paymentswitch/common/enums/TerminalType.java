/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.common.enums;

/**
 * <h1>TerminalType</h1>
 * <p>{@code PHYSICAL}: kart fiziken mevcut (EMV çip + PIN). {@code VIRTUAL}: sanal POS, kart mevcut değil (3D Secure).</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-1
 */
public enum TerminalType {
    PHYSICAL,
    VIRTUAL
}
