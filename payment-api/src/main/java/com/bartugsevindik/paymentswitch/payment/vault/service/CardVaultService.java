/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.vault.service;

import com.bartugsevindik.paymentswitch.payment.vault.dto.CardData;
import org.springframework.stereotype.Service;

/**
 * <h1>CardVaultService</h1>
 * <p>Kart verisinin bankaya gönderilene kadar şifreli saklanması (tokenization).</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-5
 */
@Service
public interface CardVaultService {

    /**
     * <h1>Kart Verisi Saklama</h1>
     * <p>Kart verisini şifreleyip saklar ve yerine kullanılacak token'ı döndürür. Ödeme ile aynı transaction içinde
     * çağrılmalıdır.</p>
     *
     * @param paymentId Ödeme ID
     * @param cardData  Kart verisi
     * @return Kart token'ı
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-5
     */
    String store(String paymentId, CardData cardData);

    /**
     * <h1>Kart Verisi Çözme</h1>
     * <p>Token'a ait kart verisini döndürür. Süresi dolmuş ya da silinmiş token için {@code NotFoundException} fırlatılır.</p>
     *
     * @param token Kart token'ı
     * @return Kart verisi
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-5
     */
    CardData detokenize(String token);

    /**
     * <h1>Kart Verisi Silme</h1>
     * <p>Banka cevabı geldikten sonra kart verisine ihtiyaç kalmaz; inquiry ve reversal sipariş numarası ile yapılır.
     * PCI DSS, CVV'nin yetkilendirme sonrası (şifreli olsa bile) saklanmasını yasaklar.</p>
     *
     * @param paymentId Ödeme ID
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-5
     */
    void purge(String paymentId);

    /**
     * <h1>Süresi Dolan Kayıtları Silme</h1>
     *
     * @return Silinen kayıt sayısı
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-5
     */
    int deleteExpired();
}
