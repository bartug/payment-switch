/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.messaging.inbox;

import org.springframework.stereotype.Service;

/**
 * <h1>InboxService</h1>
 * <p>Consumer tarafında tekrar eden mesajların ayıklanması (idempotent consumer).</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-4
 */
@Service
public interface InboxService {

    /**
     * <h1>İlk Kez İşleme Kontrolü</h1>
     * <p>Event'i işlendi olarak işaretler. {@code false} dönerse mesaj daha önce işlenmiştir ve atlanmalıdır.</p>
     * <p>İş verisiyle <b>aynı transaction</b> içinde çağrılmalıdır. İşlem rollback olursa inbox kaydı da geri alınır
     * ve mesaj tekrar geldiğinde yeniden işlenir.</p>
     *
     * @param eventId  Kafka mesajındaki {@code event-id} header'ı
     * @param consumer Consumer adı
     * @return İlk kez işleniyorsa {@code true}
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-4
     */
    boolean markProcessed(String eventId, String consumer);
}
