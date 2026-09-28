/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.bank.service;

import org.springframework.stereotype.Service;

/**
 * <h1>RecoveryService</h1>
 * <p>Cevapsız kalan işlemlerin netleştirilmesi: takılı işlem → UNKNOWN → inquiry → (gerekirse) reversal.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-5
 */
@Service
public interface RecoveryService {

    /**
     * <h1>Recovery Turu</h1>
     * <p>Takılı işlemleri UNKNOWN yapar, zamanı gelen inquiry ve reversal işlerini çalıştırır.</p>
     * <ul>
     *     <li>Inquiry işlemi bulursa: bankanın sonucu (onay, red, iptal) kesin sonuçtur.</li>
     *     <li>Inquiry işlemi bulamazsa: istek bankaya hiç ulaşmamış olabilir ya da <b>hâlâ yolda olabilir</b>.
     *     Geç ulaşıp para çekmesine karşı reversal yapılır; banka orderId'yi iptal olarak işaretler.</li>
     *     <li>Inquiry defalarca başarısız olursa: reversal.</li>
     *     <li>Reversal defalarca başarısız olursa: {@code MANUAL_REVIEW}, alarm.</li>
     * </ul>
     *
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-5
     */
    void runOnce();
}
