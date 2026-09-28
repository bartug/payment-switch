/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.routing.service;

import com.bartugsevindik.paymentswitch.routing.dto.BinInfo;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * <h1>BinLookupService</h1>
 * <p>Kart BIN'inden kartı çıkaran banka, kart tipi ve taksit programı bilgisinin bulunması.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-4
 */
@Service
public interface BinLookupService {

    /**
     * <h1>BIN Çözümleme</h1>
     * <p>En uzun prefix eşleşmesi ile BIN bilgisini döndürür: önce 8 haneye, bulunamazsa 6 haneye bakılır.
     * Sonuçlar (bulunamayanlar dahil) cache'lenir.</p>
     *
     * @param cardBin Kartın ilk 6 ya da 8 hanesi
     * @return BIN bilgisi. Tanımsızsa boş.
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-4
     */
    Optional<BinInfo> lookup(String cardBin);
}
