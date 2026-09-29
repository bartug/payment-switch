/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.ledger.entity;

import com.bartugsevindik.paymentswitch.common.entity.BaseEntity;
import com.bartugsevindik.paymentswitch.payment.ledger.enums.AccountType;
import com.bartugsevindik.paymentswitch.payment.ledger.enums.EntryDirection;
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
 * <h1>JournalLine</h1>
 * <p>Yevmiye kaydının bir satırı: hangi hesaba, hangi yönde, ne kadar. Tutar her zaman pozitiftir, yönü
 * {@code direction} belirler.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 29.09.2026 - PS-7
 */
@Entity
@Table(name = "journal_line")
@Data
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = false)
public class JournalLine extends BaseEntity {

    @Column(name = "entry_id", nullable = false, length = 36, updatable = false)
    private String entryId;

    @Column(name = "account_code", nullable = false, length = 64, updatable = false)
    private String accountCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 16, updatable = false)
    private AccountType accountType;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 6, updatable = false)
    private EntryDirection direction;

    @Column(name = "amount", nullable = false, updatable = false)
    private Long amount;

    @Column(name = "currency", nullable = false, length = 3, updatable = false)
    private String currency;
}
