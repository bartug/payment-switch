/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.routing.entity;

import com.bartugsevindik.paymentswitch.common.entity.BaseEntity;
import com.bartugsevindik.paymentswitch.common.enums.BankCode;
import com.bartugsevindik.paymentswitch.routing.enums.RoutingOutcome;
import com.bartugsevindik.paymentswitch.routing.enums.RoutingReason;
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
 * <h1>RoutingDecision</h1>
 * <p>Bir ödeme için verilen routing kararı. Hangi işlemin neden hangi bankaya gittiği sonradan incelenebilir
 * (banka bazlı hacim, failover oranı, mutabakat).</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-4
 */
@Entity
@Table(name = "routing_decision")
@Data
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = false)
public class RoutingDecision extends BaseEntity {

    @Column(name = "payment_id", nullable = false, unique = true, length = 36)
    private String paymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 16)
    private RoutingOutcome outcome;

    @Enumerated(EnumType.STRING)
    @Column(name = "bank_code", length = 16)
    private BankCode bankCode;

    @Column(name = "on_us", nullable = false)
    private Boolean onUs;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 48)
    private RoutingReason reason;

    @Column(name = "card_bin", nullable = false, length = 8)
    private String cardBin;

    @Column(name = "installment_count", nullable = false)
    private Integer installmentCount;
}
