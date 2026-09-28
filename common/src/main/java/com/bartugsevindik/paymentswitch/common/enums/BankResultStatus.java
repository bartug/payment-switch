/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.common.enums;

/**
 * <h1>BankResultStatus</h1>
 * <p>bank-adapter'ın payment-api'ye bildirdiği sonuç.</p>
 * <ul>
 *     <li>{@code UNKNOWN}: bankaya gidildi, cevap alınamadı. Para çekilmiş olabilir; inquiry ile netleştirilecek.</li>
 *     <li>{@code REVERSED}: cevapsız kalan işlem bankada geri alındı (teknik iptal).</li>
 *     <li>{@code FAILED}: istek bankaya hiç gönderilmedi (circuit açık, kart verisi alınamadı). Para çekilmediği kesin.</li>
 * </ul>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-5
 */
public enum BankResultStatus {
    APPROVED,
    DECLINED,
    UNKNOWN,
    REVERSED,
    FAILED
}
