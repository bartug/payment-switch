/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.routing.repository;

import com.bartugsevindik.paymentswitch.routing.entity.RoutingDecision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoutingDecisionRepository extends JpaRepository<RoutingDecision, Long> {

    /**
     * Ödemeye ait routing kararını getirir.
     *
     * @param paymentId Ödeme ID
     * @return Routing kararı
     */
    Optional<RoutingDecision> findByPaymentId(String paymentId);

    boolean existsByPaymentId(String paymentId);
}
