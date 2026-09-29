/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.repository;

import com.bartugsevindik.paymentswitch.payment.entity.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * Ödemeyi satır kilidi ({@code SELECT ... FOR UPDATE}) ile getirir. Aynı ödemeye eş zamanlı gelen iadeler
     * sırayla işlenir; iki kısmi iadenin toplamı ödeme tutarını aşamaz.
     *
     * @param paymentId  Ödeme ID
     * @param merchantId Üye işyeri numarası
     * @return Kilitlenmiş ödeme
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.paymentId = :paymentId AND p.merchantId = :merchantId")
    Optional<Payment> findForUpdate(@Param("paymentId") String paymentId, @Param("merchantId") String merchantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.paymentId = :paymentId")
    Optional<Payment> findForUpdate(@Param("paymentId") String paymentId);
}
