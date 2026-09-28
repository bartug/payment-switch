/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.messaging.inbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, Long> {

    /**
     * Event'i işlendi olarak kaydeder. Kayıt zaten varsa hiçbir şey yapmaz ve 0 döner.
     * Önce SELECT sonra INSERT yapmak iki consumer arasında yarışa açık olurdu; tek sorgu atomiktir.
     *
     * @param eventId  Event ID
     * @param consumer Consumer adı
     * @return Eklenen satır sayısı (1: ilk kez, 0: tekrar)
     */
    @Modifying
    @Query(value = """
            INSERT INTO processed_event (event_id, consumer, created_date, version)
            VALUES (:eventId, :consumer, now(), 0)
            ON CONFLICT (event_id, consumer) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("eventId") String eventId, @Param("consumer") String consumer);

    boolean existsByEventIdAndConsumer(String eventId, String consumer);
}
