/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.routing.enums;

/**
 * <h1>CardType</h1>
 * <p>Taksit sadece {@code CREDIT} kartlarda yapılabilir. Banka kartı ve ön ödemeli kart tek çekim çalışır.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-4
 */
public enum CardType {
    CREDIT,
    DEBIT,
    PREPAID
}
