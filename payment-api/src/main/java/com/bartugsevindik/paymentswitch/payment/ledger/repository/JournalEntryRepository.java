/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.ledger.repository;

import com.bartugsevindik.paymentswitch.payment.ledger.entity.JournalEntry;
import com.bartugsevindik.paymentswitch.payment.ledger.enums.LedgerEntryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {

    List<JournalEntry> findByPaymentIdOrderByIdAsc(String paymentId);

    Optional<JournalEntry> findByPaymentIdAndEntryType(String paymentId, LedgerEntryType entryType);

    boolean existsByEntryTypeAndReferenceId(LedgerEntryType entryType, String referenceId);
}
