/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.reconciliation.entity;

import com.bartugsevindik.paymentswitch.common.entity.BaseEntity;
import com.bartugsevindik.paymentswitch.common.enums.BankCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;

/**
 * <h1>ReconciliationRun</h1>
 * <p>Bir banka ve iş günü için yapılan mutabakat. Aynı gün için tekrar çalıştırılabilir (örn. banka dosyayı
 * düzeltip tekrar gönderdi); her çalışma ayrı kayıttır, geçmiş korunur.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 29.09.2026 - PS-7
 */
@Entity
@Table(name = "reconciliation_run")
@Data
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = false)
public class ReconciliationRun extends BaseEntity {

    @Column(name = "run_id", nullable = false, unique = true, length = 36, updatable = false)
    private String runId;

    @Enumerated(EnumType.STRING)
    @Column(name = "bank_code", nullable = false, length = 16)
    private BankCode bankCode;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(name = "matched", nullable = false)
    private Integer matched;

    @Column(name = "matched_other_day", nullable = false)
    private Integer matchedOtherDay;

    @Column(name = "missing_in_bank", nullable = false)
    private Integer missingInBank;

    @Column(name = "missing_in_ours", nullable = false)
    private Integer missingInOurs;

    @Column(name = "amount_mismatch", nullable = false)
    private Integer amountMismatch;

    @Column(name = "status_mismatch", nullable = false)
    private Integer statusMismatch;
}
