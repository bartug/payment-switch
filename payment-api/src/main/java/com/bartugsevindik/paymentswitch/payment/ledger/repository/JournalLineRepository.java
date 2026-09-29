/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.ledger.repository;

import com.bartugsevindik.paymentswitch.payment.ledger.entity.JournalLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface JournalLineRepository extends JpaRepository<JournalLine, Long> {

    List<JournalLine> findByEntryIdInOrderByIdAsc(Collection<String> entryIds);

    List<JournalLine> findByEntryIdOrderByIdAsc(String entryId);

    /**
     * Hesap bazında borç ve alacak toplamları. Satırlar hiç güncellenmediği için bakiye her zaman satırlardan
     * türetilebilir; ayrı bir bakiye kolonu tutulmaz (tutulsaydı satırlarla tutarsız kalabilirdi).
     *
     * @param prefix Hesap kodu ön eki (örn. {@code MERCHANT_PAYABLE:MRC0000001}). Hepsi için boş string.
     * @return [hesap kodu, hesap tipi, para birimi, borç toplamı, alacak toplamı]
     */
    @Query("""
            SELECT l.accountCode, l.accountType, l.currency,
                   SUM(CASE WHEN l.direction = com.bartugsevindik.paymentswitch.payment.ledger.enums.EntryDirection.DEBIT THEN l.amount ELSE 0 END),
                   SUM(CASE WHEN l.direction = com.bartugsevindik.paymentswitch.payment.ledger.enums.EntryDirection.CREDIT THEN l.amount ELSE 0 END)
            FROM JournalLine l
            WHERE l.accountCode LIKE CONCAT(:prefix, '%')
            GROUP BY l.accountCode, l.accountType, l.currency
            ORDER BY l.accountCode
            """)
    List<Object[]> sumByAccount(@Param("prefix") String prefix);

    /**
     * @return [borç toplamı, alacak toplamı]
     */
    @Query("""
            SELECT COALESCE(SUM(CASE WHEN l.direction = com.bartugsevindik.paymentswitch.payment.ledger.enums.EntryDirection.DEBIT THEN l.amount ELSE 0 END), 0),
                   COALESCE(SUM(CASE WHEN l.direction = com.bartugsevindik.paymentswitch.payment.ledger.enums.EntryDirection.CREDIT THEN l.amount ELSE 0 END), 0)
            FROM JournalLine l
            """)
    List<Object[]> totals();
}
