/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.repository;

import com.bartugsevindik.paymentswitch.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /**
     * Dışarıya açılan ödeme ID'si ile kaydı getirir.
     *
     * @param paymentId Ödeme ID
     * @return Ödeme kaydı
     */
    Optional<Payment> findByPaymentId(String paymentId);

    /**
     * Ödemeyi sadece ait olduğu üye işyeri için getirir. Başka üye işyerinin ödemesi yokmuş gibi davranılır (IDOR).
     *
     * @param paymentId  Ödeme ID
     * @param merchantId Üye işyeri numarası
     * @return Ödeme kaydı
     */
    Optional<Payment> findByPaymentIdAndMerchantId(String paymentId, String merchantId);
}
