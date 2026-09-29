/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.reconciliation.repository;

import com.bartugsevindik.paymentswitch.payment.reconciliation.entity.ReconciliationItem;
import com.bartugsevindik.paymentswitch.payment.reconciliation.enums.ReconciliationResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ReconciliationItemRepository extends JpaRepository<ReconciliationItem, Long> {

    List<ReconciliationItem> findByRunIdOrderByIdAsc(String runId);

    List<ReconciliationItem> findByRunIdAndResultInOrderByIdAsc(String runId, Collection<ReconciliationResult> results);
}
