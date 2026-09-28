/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.routing.entity;

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

/**
 * <h1>AcquirerBank</h1>
 * <p>Switch'in işlem gönderebildiği, POS anlaşması olan banka ve komisyon oranları.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-4
 */
@Entity
@Table(name = "acquirer_bank")
@Data
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = false)
public class AcquirerBank extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "bank_code", nullable = false, unique = true, length = 16)
    private BankCode bankCode;

    /**
     * Pasif bankaya işlem gönderilmez. Tek çekim işlemler başka bankaya kayar, taksitli işlemler reddedilir.
     */
    @Column(name = "active", nullable = false)
    private Boolean active;

    /**
     * bank-adapter'daki circuit breaker durumundan otomatik gelir. {@code active} operasyonun elle verdiği karardır;
     * işlem gönderilmesi için ikisinin de {@code true} olması gerekir.
     */
    @Column(name = "healthy", nullable = false)
    private Boolean healthy;

    /**
     * Kart bu bankanınsa uygulanan komisyon (baz puan, 180 = %1,80).
     */
    @Column(name = "on_us_rate_bps", nullable = false)
    private Integer onUsRateBps;

    /**
     * Kart başka bankanınsa uygulanan komisyon. Interchange nedeniyle on-us oranından yüksektir.
     */
    @Column(name = "off_us_rate_bps", nullable = false)
    private Integer offUsRateBps;
}
