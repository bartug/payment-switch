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
     * <p>Şu an işlem alabilen (aktif ve sağlıklı) bankaları döndürür. Kısa süreli cache'lenir.</p>
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

    /**
     * <h1>Banka Sağlık Durumu Güncelleme</h1>
     * <p>bank-adapter'dan gelen circuit breaker durumuna göre bankayı otomatik olarak işlem almaz duruma getirir
     * ya da geri açar. Operasyonun verdiği aktif/pasif kararına dokunmaz.</p>
     *
     * @param bankCode Banka kodu
     * @param healthy  Circuit breaker kapalı mı
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-5
     */
    void updateBankHealth(BankCode bankCode, boolean healthy);
}
