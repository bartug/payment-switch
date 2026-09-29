/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.reconciliation.entity;

import com.bartugsevindik.paymentswitch.common.entity.BaseEntity;
import com.bartugsevindik.paymentswitch.payment.reconciliation.enums.ReconciliationResult;
import com.bartugsevindik.paymentswitch.payment.reconciliation.enums.SettlementRecordType;
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
 * <h1>ReconciliationItem</h1>
 * <p>Mutabakatın tek bir satırı: hangi kayıt, sonuç ve iki taraftaki değerler.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 29.09.2026 - PS-7
 */
@Entity
@Table(name = "reconciliation_item")
@Data
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = false)
public class ReconciliationItem extends BaseEntity {

    @Column(name = "run_id", nullable = false, length = 36)
    private String runId;

    @Enumerated(EnumType.STRING)
    @Column(name = "record_type", nullable = false, length = 8)
    private SettlementRecordType recordType;

    @Column(name = "order_id", nullable = false, length = 36)
    private String orderId;

    @Column(name = "operation_id", length = 36)
    private String operationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 24)
    private ReconciliationResult result;

    @Column(name = "our_amount")
    private Long ourAmount;

    @Column(name = "bank_amount")
    private Long bankAmount;

    @Column(name = "our_status", length = 20)
    private String ourStatus;

    @Column(name = "detail")
    private String detail;
}
