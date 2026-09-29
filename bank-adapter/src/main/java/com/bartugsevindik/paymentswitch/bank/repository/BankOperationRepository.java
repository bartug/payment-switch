/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.bank.repository;

import com.bartugsevindik.paymentswitch.bank.entity.BankOperation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BankOperationRepository extends JpaRepository<BankOperation, Long> {

    boolean existsByOperationId(String operationId);

    Optional<BankOperation> findByOperationId(String operationId);

    /**
     * Tekrar deneme zamanı gelmiş işlemleri kilitleyerek getirir.
     *
     * @param now   Şu anki zaman
     * @param limit En fazla kayıt
     * @return Kilitlenmiş işlemler
     */
    @Query(value = """
            SELECT * FROM bank_operation
            WHERE status = 'PENDING' AND next_attempt_at <= :now
            ORDER BY next_attempt_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<BankOperation> lockDue(@Param("now") LocalDateTime now, @Param("limit") int limit);
}
