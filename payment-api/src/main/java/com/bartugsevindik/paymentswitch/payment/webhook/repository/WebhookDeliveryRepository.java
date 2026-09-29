/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.webhook.repository;

import com.bartugsevindik.paymentswitch.payment.webhook.entity.WebhookDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, Long> {

    /**
     * Gönderim zamanı gelmiş bildirimleri kilitleyerek getirir. Birden fazla pod aynı bildirimi almaz.
     *
     * @param now   Şu anki zaman
     * @param limit En fazla kayıt
     * @return Kilitlenmiş bildirimler
     */
    @Query(value = """
            SELECT * FROM webhook_delivery
            WHERE status = 'PENDING' AND next_attempt_at <= :now
            ORDER BY next_attempt_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<WebhookDelivery> lockDue(@Param("now") LocalDateTime now, @Param("limit") int limit);

    List<WebhookDelivery> findByPaymentIdOrderByIdAsc(String paymentId);

    Optional<WebhookDelivery> findByDeliveryId(String deliveryId);
}
