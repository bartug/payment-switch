/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.idempotency.lock;

/**
 * <h1>LockResult</h1>
 * <p>{@code SKIPPED}: Redis'e ulaşılamadı, kilit alınmadan devam ediliyor (fail-open). Bu durumda
 * kilit bırakılmaya çalışılmaz; zaten kapalı olan Redis için ikinci kez timeout beklenmez.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-2
 */
public enum LockResult {
    ACQUIRED,
    HELD_BY_OTHER,
    SKIPPED;

    public boolean canProceed() {
        return this != HELD_BY_OTHER;
    }
}
