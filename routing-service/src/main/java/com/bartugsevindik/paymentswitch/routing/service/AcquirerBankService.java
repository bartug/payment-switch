/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.routing.service;

import com.bartugsevindik.paymentswitch.common.enums.BankCode;
import com.bartugsevindik.paymentswitch.routing.dto.AcquirerBankInfo;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <h1>AcquirerBankService</h1>
 * <p>İşlem gönderilebilen bankaların listelenmesi ve aktif/pasif yönetimi.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-4
 */
@Service
public interface AcquirerBankService {

    /**
     * <h1>Aktif Bankaları Getirme</h1>
     * <p>Şu an işlem alabilen bankaları döndürür. Kısa süreli cache'lenir.</p>
     *
     * @return Aktif bankalar
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-4
     */
    List<AcquirerBankInfo> getActiveBanks();

    /**
     * <h1>Tüm Bankaları Getirme</h1>
     *
     * @return Tüm bankalar
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-4
     */
    List<AcquirerBankInfo> getAllBanks();

    /**
     * <h1>Banka Durumu Güncelleme</h1>
     * <p>Pasife alınan bankaya yeni işlem gönderilmez. Bu pod'da hemen, diğer pod'larda cache süresi kadar sonra geçerli olur.</p>
     *
     * @param bankCode Banka kodu
     * @param active   Yeni durum
     * @return Güncel banka bilgisi
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-4
     */
    AcquirerBankInfo updateBankStatus(BankCode bankCode, boolean active);
}
