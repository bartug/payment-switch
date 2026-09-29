/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.merchant.repository;

import com.bartugsevindik.paymentswitch.payment.merchant.entity.Merchant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MerchantRepository extends JpaRepository<Merchant, Long> {

    /**
     * Üye işyeri numarası ile kaydı getirir.
     *
     * @param merchantId Üye işyeri numarası
     * @return Üye işyeri
     */
    Optional<Merchant> findByMerchantId(String merchantId);

    boolean existsByMerchantId(String merchantId);
}
