/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.bank.service;

import com.bartugsevindik.paymentswitch.common.event.BankAuthorizationRequestedEvent;
import com.bartugsevindik.paymentswitch.messaging.consumer.IncomingEvent;
import org.springframework.stereotype.Service;

/**
 * <h1>AuthorizationService</h1>
 * <p>Routing'den gelen isteğin bankaya gönderilmesi.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-5
 */
@Service
public interface AuthorizationService {

    /**
     * <h1>Bankaya Gönderme</h1>
     * <p>Akış: tekrar mı → kart verisini al → işlemi sahiplen (TX) → bankayı çağır (TX dışında) → sonucu yaz (TX).</p>
     * <p>Satış isteği <b>asla retry edilmez</b>. Cevap alınamazsa işlem {@code UNKNOWN} olur ve recovery job'u
     * inquiry ile netleştirir.</p>
     *
     * @param event {@code bank.requests.{BANKA}} topic'inden gelen istek
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-5
     */
    void authorize(IncomingEvent<BankAuthorizationRequestedEvent> event);
}
