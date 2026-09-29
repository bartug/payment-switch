/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.bank.service;

import com.bartugsevindik.paymentswitch.common.event.BankOperationRequestedEvent;
import com.bartugsevindik.paymentswitch.messaging.consumer.IncomingEvent;
import org.springframework.stereotype.Service;

/**
 * <h1>BankOperationService</h1>
 * <p>İptal ve iade isteklerinin bankaya gönderilmesi.</p>
 * <p>Satıştan farklı olarak cevapsız kalan istek <b>tekrar denenir</b>: banka operationId'yi tanır, aynı ID ile gelen
 * ikinci istek ikinci iadeyi oluşturmaz. Retry'ın güvenli olup olmadığını belirleyen şey hedef sistemin idempotency'sidir.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 29.09.2026 - PS-6
 */
@Service
public interface BankOperationService {

    /**
     * <h1>İşlemi Bankaya Gönderme</h1>
     *
     * @param event {@code bank.requests.{BANKA}} topic'inden gelen iptal / iade isteği
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 29.09.2026 - PS-6
     */
    void process(IncomingEvent<BankOperationRequestedEvent> event);

    /**
     * <h1>Bekleyen İşlemleri Tekrar Deneme</h1>
     *
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 29.09.2026 - PS-6
     */
    void retryDue();
}
