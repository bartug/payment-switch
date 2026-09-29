/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.ledger.service;

import com.bartugsevindik.paymentswitch.payment.entity.Payment;
import com.bartugsevindik.paymentswitch.payment.ledger.dto.AccountBalanceDTO;
import com.bartugsevindik.paymentswitch.payment.ledger.dto.JournalEntryDTO;
import com.bartugsevindik.paymentswitch.payment.ledger.dto.TrialBalanceDTO;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <h1>LedgerService</h1>
 * <p>Para hareketlerinin çift taraflı muhasebe kayıtları. Kayıtlar ödeme durum değişikliğiyle <b>aynı transaction</b>
 * içinde yazılır; ödeme onaylandıysa kaydı kesin vardır.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 29.09.2026 - PS-7
 */
@Service
public interface LedgerService {

    /**
     * <h1>Satış Kaydı</h1>
     * <pre>
     * Borç   BANK_RECEIVABLE:{BANKA}    tutar
     * Alacak MERCHANT_PAYABLE:{İŞYERİ}  tutar - komisyon
     * Alacak FEE_REVENUE                komisyon
     * </pre>
     *
     * @param payment Onaylanan ödeme
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 29.09.2026 - PS-7
     */
    void postSale(Payment payment);

    /**
     * <h1>İade Kaydı</h1>
     * <p>Komisyon iade edilmez; iade tutarı üye işyerinin alacağından düşülür.</p>
     * <pre>
     * Borç   MERCHANT_PAYABLE:{İŞYERİ}  iade
     * Alacak BANK_RECEIVABLE:{BANKA}    iade
     * </pre>
     *
     * @param payment     Ödeme
     * @param operationId İade işlemi ID
     * @param amount      İade tutarı (kuruş)
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 29.09.2026 - PS-7
     */
    void postRefund(Payment payment, String operationId, long amount);

    /**
     * <h1>İptal Kaydı</h1>
     * <p>Satış kaydının ters kaydıdır (storno): aynı satırlar, ters yönde. Komisyon dahil her şey geri alınır;
     * işlem takasa hiç girmemiştir.</p>
     *
     * @param payment     İptal edilen ödeme
     * @param operationId İptal işlemi ID
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 29.09.2026 - PS-7
     */
    void postVoid(Payment payment, String operationId);

    List<JournalEntryDTO> getEntries(String paymentId);

    List<AccountBalanceDTO> getBalances(String accountPrefix);

    /**
     * <h1>Mizan</h1>
     * <p>Tüm kayıtların borç ve alacak toplamı. Eşit değilse sistemde para oluşmuş ya da kaybolmuştur.</p>
     *
     * @return Mizan
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 29.09.2026 - PS-7
     */
    TrialBalanceDTO getTrialBalance();
}
