/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.bank.repository;

import com.bartugsevindik.paymentswitch.bank.entity.BankTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BankTransactionRepository extends JpaRepository<BankTransaction, Long> {

    /**
     * Ödemeye ait banka işlemini getirir.
     *
     * @param paymentId Ödeme ID
     * @return Banka işlemi
     */
    Optional<BankTransaction> findByPaymentId(String paymentId);

    boolean existsByPaymentId(String paymentId);

    /**
     * Zamanı gelmiş inquiry ve reversal işlerini kilitleyerek getirir. Birden fazla pod aynı işi almaz.
     *
     * @param now   Şu anki zaman
     * @param limit En fazla kayıt
     * @return Kilitlenmiş işlemler
     */
    @Query(value = """
            SELECT * FROM bank_transaction
            WHERE status IN ('UNKNOWN', 'REVERSING') AND next_attempt_at <= :now
            ORDER BY next_attempt_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<BankTransaction> lockDue(@Param("now") LocalDateTime now, @Param("limit") int limit);

    /**
     * {@code SENDING} durumunda takılı kalmış işlemleri kilitleyerek getirir.
     *
     * @param before Bu tarihten önce güncellenmiş olanlar
     * @param limit  En fazla kayıt
     * @return Kilitlenmiş işlemler
     */
    @Query(value = """
            SELECT * FROM bank_transaction
            WHERE status = 'SENDING' AND created_date < :before
            ORDER BY id
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<BankTransaction> lockStuckSending(@Param("before") LocalDateTime before, @Param("limit") int limit);
}
