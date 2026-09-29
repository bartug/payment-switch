/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.operation.repository;

import com.bartugsevindik.paymentswitch.common.enums.BankOperationType;
import com.bartugsevindik.paymentswitch.payment.operation.entity.PaymentOperation;
import com.bartugsevindik.paymentswitch.payment.operation.enums.PaymentOperationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentOperationRepository extends JpaRepository<PaymentOperation, Long> {

    Optional<PaymentOperation> findByOperationId(String operationId);

    List<PaymentOperation> findByPaymentIdOrderByIdAsc(String paymentId);

    boolean existsByPaymentIdAndStatus(String paymentId, PaymentOperationStatus status);

    /**
     * Bankadan sonucu beklenen iadelerin toplamı. Yeni iade tutarı hesaplanırken bu tutar ayrılmış sayılır;
     * aksi halde sonucu beklenen iki iade birlikte ödeme tutarını aşabilirdi.
     *
     * @param paymentId Ödeme ID
     * @param type      İşlem tipi
     * @param status    Durum
     * @return Toplam tutar (kuruş)
     */
    @Query("SELECT COALESCE(SUM(o.amount), 0) FROM PaymentOperation o WHERE o.paymentId = :paymentId AND o.type = :type AND o.status = :status")
    long sumAmount(@Param("paymentId") String paymentId, @Param("type") BankOperationType type,
                   @Param("status") PaymentOperationStatus status);
}
