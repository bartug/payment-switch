/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.messaging.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Gönderilmemiş event'leri sırayla kilitleyerek getirir. {@code SKIP LOCKED} sayesinde birden fazla pod
     * aynı anda relay çalıştırsa da aynı kaydı iki pod almaz; kilitli satırlar atlanır.
     *
     * @param limit Bir seferde alınacak kayıt sayısı
     * @return Kilitlenmiş event'ler
     */
    @Query(value = """
            SELECT * FROM outbox_event
            WHERE published_at IS NULL
            ORDER BY id
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> lockNextBatch(@Param("limit") int limit);

    /**
     * Gönderilmemiş event sayısı. Sürekli artıyorsa Kafka'ya yazılamıyor demektir.
     *
     * @return Bekleyen event sayısı
     */
    @Query("SELECT count(o) FROM OutboxEvent o WHERE o.publishedAt IS NULL")
    long countPending();

    /**
     * En eski bekleyen event'in oluşturulma zamanı. Outbox gecikmesi (lag) için kullanılır.
     *
     * @return En eski bekleyen event zamanı
     */
    @Query("SELECT min(o.createdDate) FROM OutboxEvent o WHERE o.publishedAt IS NULL")
    Optional<LocalDateTime> findOldestPendingCreatedDate();

    Optional<OutboxEvent> findFirstByAggregateIdOrderByIdDesc(String aggregateId);

    /**
     * Gönderilmiş ve saklama süresi dolmuş event'leri parça parça siler.
     *
     * @param before Bu tarihten önce gönderilenler silinir
     * @param limit  Bir seferde silinecek en fazla kayıt
     * @return Silinen kayıt sayısı
     */
    @Modifying
    @Query(value = """
            DELETE FROM outbox_event
            WHERE id IN (SELECT id FROM outbox_event WHERE published_at < :before LIMIT :limit)
            """, nativeQuery = true)
    int deletePublishedBefore(@Param("before") LocalDateTime before, @Param("limit") int limit);
}
