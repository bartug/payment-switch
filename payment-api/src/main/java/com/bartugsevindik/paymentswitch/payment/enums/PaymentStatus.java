/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.enums;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * <h1>PaymentStatus</h1>
 * <p>Ödemenin yaşam döngüsü. Geçişler {@link #canTransitionTo(PaymentStatus)} ile kontrol edilir,
 * tanımlı olmayan bir geçiş denenirse entity exception fırlatır.</p>
 * <p>{@code UNKNOWN}: bankaya gidildi ama cevap alınamadı. Para çekilmiş olabilir, bu yüzden retry edilmez;
 * inquiry veya reversal ile netleştirilir.</p>
 * <p>Routing sonucu ile banka sonucu farklı topic'lerden gelir ve aralarında sıra garantisi yoktur. Banka sonucu
 * önce gelebileceği için {@code PENDING} durumundan doğrudan banka sonucuna geçilebilir.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-1
 */
public enum PaymentStatus {
    PENDING,
    ROUTED,
    APPROVED,
    DECLINED,
    UNKNOWN,
    REVERSED,
    VOIDED,
    REFUNDED,
    FAILED;

    private static final Map<PaymentStatus, Set<PaymentStatus>> TRANSITIONS = Map.of(
            PENDING, EnumSet.of(ROUTED, FAILED, APPROVED, DECLINED, UNKNOWN),
            ROUTED, EnumSet.of(APPROVED, DECLINED, UNKNOWN, FAILED),
            UNKNOWN, EnumSet.of(APPROVED, DECLINED, REVERSED),
            APPROVED, EnumSet.of(VOIDED, REFUNDED)
    );

    public boolean canTransitionTo(PaymentStatus target) {
        return TRANSITIONS.getOrDefault(this, EnumSet.noneOf(PaymentStatus.class)).contains(target);
    }

    public boolean isFinal() {
        return !TRANSITIONS.containsKey(this);
    }
}
