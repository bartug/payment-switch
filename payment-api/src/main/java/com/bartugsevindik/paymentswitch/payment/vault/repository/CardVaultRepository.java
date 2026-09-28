/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.vault.repository;

import com.bartugsevindik.paymentswitch.payment.vault.entity.CardVaultEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface CardVaultRepository extends JpaRepository<CardVaultEntry, Long> {

    /**
     * Süresi dolmamış kaydı token ile getirir.
     *
     * @param token Kart token'ı
     * @param now   Şu anki zaman
     * @return Kayıt
     */
    Optional<CardVaultEntry> findByTokenAndExpiresAtAfter(String token, LocalDateTime now);

    /**
     * Ödemeye ait kart verisini siler.
     *
     * @param paymentId Ödeme ID
     * @return Silinen kayıt sayısı
     */
    @Modifying
    @Query("DELETE FROM CardVaultEntry c WHERE c.paymentId = :paymentId")
    int deleteByPaymentId(@Param("paymentId") String paymentId);

    /**
     * Süresi dolan kayıtları siler.
     *
     * @param now Şu anki zaman
     * @return Silinen kayıt sayısı
     */
    @Modifying
    @Query("DELETE FROM CardVaultEntry c WHERE c.expiresAt < :now")
    int deleteExpired(@Param("now") LocalDateTime now);
}
