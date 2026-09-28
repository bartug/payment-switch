/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.bank.service;

import com.bartugsevindik.paymentswitch.bank.dto.BankOutcome;
import com.bartugsevindik.paymentswitch.bank.entity.BankTransaction;
import com.bartugsevindik.paymentswitch.common.event.BankAuthorizationRequestedEvent;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * <h1>BankTransactionService</h1>
 * <p>Banka işlemi kayıtlarının DB tarafı. Her metot kısa bir transaction'dır; banka çağrısı hiçbir zaman
 * transaction içinde yapılmaz (banka 5 sn cevap vermezse DB connection'ı 5 sn tutulurdu).</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-5
 */
@Service
public interface BankTransactionService {

    /**
     * <h1>İşlemi Sahiplenme</h1>
     * <p>Inbox kaydı ile birlikte işlemi {@code SENDING} olarak yazar. Aynı event ya da aynı ödeme daha önce
     * işlendiyse boş döner ve bankaya gidilmez.</p>
     *
     * @param eventId Kafka event ID
     * @param request Banka isteği
     * @return Sahiplenilen işlem
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-5
     */
    Optional<BankTransaction> claim(String eventId, BankAuthorizationRequestedEvent request);

    /**
     * <h1>Gönderilmeden Başarısız</h1>
     * <p>İstek bankaya hiç gönderilmeden başarısız olduysa (kart verisi yok) işlemi doğrudan {@code FAILED} yazar.</p>
     *
     * @param eventId Kafka event ID
     * @param request Banka isteği
     * @param reason  Sebep
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-5
     */
    void recordNotSent(String eventId, BankAuthorizationRequestedEvent request, String reason);

    /**
     * <h1>Sonucu Kaydetme</h1>
     * <p>İşlemin durumunu günceller ve payment-api'ye gidecek sonucu outbox'a yazar. Sonuçlanmış işleme dokunmaz.</p>
     *
     * @param transactionId İşlem ID
     * @param outcome       Banka sonucu
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-5
     */
    void complete(Long transactionId, BankOutcome outcome);

    /**
     * <h1>Takılı İşlemleri Kurtarma</h1>
     * <p>{@code SENDING}'de takılı işlemleri {@code UNKNOWN} yapar. Uygulama banka cevabını beklerken ölmüştür;
     * istek bankaya gitmiş olabilir.</p>
     *
     * @return Kurtarılan işlem sayısı
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-5
     */
    int recoverStuckSending();

    /**
     * <h1>Zamanı Gelen İşleri Alma</h1>
     * <p>Inquiry ya da reversal zamanı gelmiş işlemleri kilitler ve bir süre başka pod'ların almaması için ileri erteler.</p>
     *
     * @return İşlenecek işlemler
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-5
     */
    List<BankTransaction> claimDue();

    void startReversal(Long transactionId, String reason);

    void inquiryFailed(Long transactionId, String error);

    void reversalFailed(Long transactionId, String error);
}
