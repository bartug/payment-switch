/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.routing.entity;

import com.bartugsevindik.paymentswitch.common.entity.BaseEntity;
import com.bartugsevindik.paymentswitch.common.enums.BankCode;
import com.bartugsevindik.paymentswitch.routing.enums.CardProgram;
import com.bartugsevindik.paymentswitch.routing.enums.CardScheme;
import com.bartugsevindik.paymentswitch.routing.enums.CardType;
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
 * <h1>BinRange</h1>
 * <p>Kartın ilk 6 veya 8 hanesinden kartı çıkaran banka, kart tipi ve taksit programı bilgisi.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-4
 */
@Entity
@Table(name = "bin_range")
@Data
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = false)
public class BinRange extends BaseEntity {

    @Column(name = "bin_prefix", nullable = false, unique = true, length = 8)
    private String binPrefix;

    /**
     * Kartı çıkaran banka (issuer). POS'u olan banka (acquirer) ile aynıysa işlem on-us olur.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "issuer_bank", nullable = false, length = 16)
    private BankCode issuerBank;

    /**
     * Taksit programı. Banka kartı ve ön ödemeli kartlarda boştur.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "card_program", length = 16)
    private CardProgram cardProgram;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_scheme", nullable = false, length = 16)
    private CardScheme cardScheme;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", nullable = false, length = 16)
    private CardType cardType;

    @Column(name = "commercial", nullable = false)
    private Boolean commercial;
}
