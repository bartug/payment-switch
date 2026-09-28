/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.common.event;

import com.bartugsevindik.paymentswitch.common.enums.BankCode;

import java.time.Instant;

/**
 * <h1>BankHealthChangedEvent</h1>
 * <p>bank-adapter'daki circuit breaker durumu değiştiğinde ve periyodik olarak basılır. routing-service bu event ile
 * bankayı otomatik olarak işlem almaz duruma getirir ya da geri açar.</p>
 * <p>Bir olay değil, <b>durum</b> bildirimidir; kaybolan mesaj bir sonraki periyodik bildirimle düzelir.</p>
 *
 * @param healthy      Bankaya işlem gönderilebilir mi
 * @param circuitState Circuit breaker durumu (CLOSED, OPEN, HALF_OPEN)
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-5
 */
public record BankHealthChangedEvent(BankCode bankCode, boolean healthy, String circuitState, Instant occurredAt) {

    public static final String TOPIC = "bank.health";
}
