/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.idempotency.repository;

import com.bartugsevindik.paymentswitch.payment.idempotency.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {

    /**
     * Üye işyeri ve key ile kaydı getirir.
     *
     * @param merchantId     Üye işyeri numarası
     * @param idempotencyKey Idempotency-Key
     * @return Kayıt
     */
    Optional<IdempotencyRecord> findByMerchantIdAndIdempotencyKey(String merchantId, String idempotencyKey);

    /**
     * Süresi dolan kayıtları parça parça siler. Tek seferde büyük DELETE tabloyu uzun süre kilitlememesi için limitlidir.
     *
     * @param now   Şu anki zaman
     * @param limit Bir seferde silinecek en fazla kayıt
     * @return Silinen kayıt sayısı
     */
    @Modifying
    @Query(value = """
            DELETE FROM idempotency_record
            WHERE id IN (SELECT id FROM idempotency_record WHERE expires_at < :now LIMIT :limit)
            """, nativeQuery = true)
    int deleteExpired(@Param("now") LocalDateTime now, @Param("limit") int limit);
}
