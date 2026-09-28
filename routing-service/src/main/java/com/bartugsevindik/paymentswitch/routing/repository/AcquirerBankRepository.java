/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.routing.repository;

import com.bartugsevindik.paymentswitch.common.enums.BankCode;
import com.bartugsevindik.paymentswitch.routing.entity.AcquirerBank;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AcquirerBankRepository extends JpaRepository<AcquirerBank, Long> {

    /**
     * Banka koduna göre kaydı getirir.
     *
     * @param bankCode Banka kodu
     * @return Banka kaydı
     */
    Optional<AcquirerBank> findByBankCode(BankCode bankCode);
}
