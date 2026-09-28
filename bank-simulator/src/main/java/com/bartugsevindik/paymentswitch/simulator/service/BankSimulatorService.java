/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.simulator.service;

import com.bartugsevindik.paymentswitch.common.enums.BankCode;
import com.bartugsevindik.paymentswitch.simulator.dto.BankAuthorizeRequest;
import com.bartugsevindik.paymentswitch.simulator.dto.BankTransactionResponse;
import com.bartugsevindik.paymentswitch.simulator.dto.ChaosSettings;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * <h1>BankSimulatorService</h1>
 * <p>Sahte banka: satış, sorgulama, iptal ve hata senaryoları.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-5
 */
@Service
public interface BankSimulatorService {

    /**
     * <h1>Satış</h1>
     * <p>Aynı orderId ile gelen ikinci istek yeni işlem oluşturmaz, ilk sonucu döner. Tutarın son iki hanesi sonucu
     * belirler: 51 yetersiz bakiye, 05 onaylanmadı, 54 süresi dolmuş kart, diğerleri onay.</p>
     *
     * @param bankCode Banka
     * @param request  Satış isteği
     * @return İşlem sonucu
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-5
     */
    BankTransactionResponse authorize(BankCode bankCode, BankAuthorizeRequest request);

    /**
     * <h1>İşlem Sorgulama</h1>
     *
     * @param bankCode Banka
     * @param orderId  Sipariş numarası
     * @return İşlem. Banka işlemi hiç almadıysa boş.
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-5
     */
    Optional<BankTransactionResponse> inquire(BankCode bankCode, String orderId);

    /**
     * <h1>Teknik İptal (Reversal)</h1>
     * <p>İşlem yoksa da orderId iptal olarak işaretlenir; asıl istek bankaya sonradan ulaşırsa reddedilir.</p>
     *
     * @param bankCode Banka
     * @param orderId  Sipariş numarası
     * @return İptal sonucu
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-5
     */
    BankTransactionResponse reverse(BankCode bankCode, String orderId);

    /**
     * <h1>Echo</h1>
     * <p>ISO 8583 0800 ağ yönetimi mesajının karşılığı. Banka ayakta mı?</p>
     *
     * @param bankCode Banka
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-5
     */
    void echo(BankCode bankCode);

    ChaosSettings getChaos(BankCode bankCode);

    Map<BankCode, ChaosSettings> getAllChaos();

    void updateChaos(BankCode bankCode, ChaosSettings settings);
}
