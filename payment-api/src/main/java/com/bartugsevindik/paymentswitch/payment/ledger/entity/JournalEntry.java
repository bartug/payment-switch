/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.ledger.entity;

import com.bartugsevindik.paymentswitch.common.entity.BaseEntity;
import com.bartugsevindik.paymentswitch.payment.ledger.enums.LedgerEntryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * <h1>JournalEntry</h1>
 * <p>Yevmiye kaydı. Bir para hareketini (satış, iade, iptal) temsil eder; satırlarının borç toplamı alacak toplamına
 * eşittir. Eşitliği commit anında veritabanı trigger'ı kontrol eder.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 29.09.2026 - PS-7
 */
@Entity
@Table(name = "journal_entry")
@Data
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = false)
public class JournalEntry extends BaseEntity {

    @Column(name = "entry_id", nullable = false, unique = true, length = 36, updatable = false)
    private String entryId;

    @Column(name = "payment_id", nullable = false, length = 36, updatable = false)
    private String paymentId;

    /**
     * Kaydı oluşturan olay. {@code (entryType, referenceId)} unique; aynı olay iki kez muhasebeleşemez.
     */
    @Column(name = "reference_id", nullable = false, length = 36, updatable = false)
    private String referenceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 16, updatable = false)
    private LedgerEntryType entryType;

    @Column(name = "currency", nullable = false, length = 3, updatable = false)
    private String currency;
}
