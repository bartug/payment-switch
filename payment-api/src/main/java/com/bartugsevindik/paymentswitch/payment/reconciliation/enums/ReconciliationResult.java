/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.reconciliation.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * <h1>ReconciliationResult</h1>
 * <p>Bizim kaydımız ile bankanın gün sonu dosyasının karşılaştırma sonucu.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 29.09.2026 - PS-7
 */
@Getter
@RequiredArgsConstructor
public enum ReconciliationResult {
    MATCHED(false),
    /**
     * Gün sonu saatine yakın işlem bankada bir önceki ya da sonraki güne yazılmış. Hata değil; bilgi amaçlı.
     */
    MATCHED_DIFFERENT_DAY(false),
    /**
     * Bizde onaylı, bankada yok. Üye işyerine gelmeyecek parayı ödeme riski.
     */
    MISSING_IN_BANK(true),
    /**
     * Bankada var, bizde hiç yok. Kart sahibinden para çekilmiş ama bizim haberimiz yok.
     */
    MISSING_IN_OURS(true),
    AMOUNT_MISMATCH(true),
    /**
     * Bizde başarısız / iptal / cevapsız, bankada başarılı. Kart sahibinden para çekilmiş ama üye işyerine bildirilmemiş;
     * çift çekimin tipik sebebi (kart sahibi başarısız sanıp tekrar ödemiştir).
     */
    STATUS_MISMATCH(true);

    private final boolean requiresAction;
}
