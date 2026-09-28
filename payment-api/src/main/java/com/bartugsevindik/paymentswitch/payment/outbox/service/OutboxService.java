/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.outbox.service;

import org.springframework.stereotype.Service;

/**
 * <h1>OutboxService</h1>
 * <p>Kafka'ya gönderilecek event'lerin outbox tablosuna yazılması ve temizlenmesi.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-3
 */
@Service
public interface OutboxService {

    /**
     * <h1>Event Kaydetme</h1>
     * <p>Event'i outbox tablosuna yazar. İş verisiyle <b>aynı transaction</b> içinde çağrılmak zorundadır;
     * transaction yoksa exception fırlatılır. Kafka'ya gönderim relay tarafından asenkron yapılır.</p>
     *
     * @param aggregateType Event'in ait olduğu kaynak tipi (örn. PAYMENT)
     * @param aggregateId   Kaynak ID. Kafka mesaj key'i olarak da kullanılır.
     * @param topic         Hedef topic
     * @param event         Event nesnesi, JSON'a çevrilir
     * @return Oluşan event ID
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-3
     */
    String enqueue(String aggregateType, String aggregateId, String topic, Object event);

    /**
     * <h1>Gönderilmiş Event'leri Silme</h1>
     * <p>Saklama süresi dolan gönderilmiş event'leri siler.</p>
     *
     * @return Silinen kayıt sayısı
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-3
     */
    int deletePublishedEvents();
}
