/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.common.model;

import com.bartugsevindik.paymentswitch.common.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void ondalikTutarKurusaCevrilir() {
        Money money = Money.of(new BigDecimal("150.75"), "TRY");

        assertThat(money.amount()).isEqualTo(15075);
        assertThat(money.toDecimal()).isEqualByComparingTo("150.75");
    }

    @Test
    void ucHaneliKusuratReddedilir() {
        assertThatThrownBy(() -> Money.of(new BigDecimal("10.005"), "TRY"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void sondakiSifirlarSorunOlmaz() {
        assertThat(Money.of(new BigDecimal("10.500"), "TRY").amount()).isEqualTo(1050);
    }

    @Test
    void kusuratsizParaBirimi() {
        assertThat(Money.of(new BigDecimal("1500"), "JPY").amount()).isEqualTo(1500);
    }

    @Test
    void negatifTutarReddedilir() {
        assertThatThrownBy(() -> Money.ofMinor(-1, "TRY")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void gecersizParaBirimi() {
        assertThatThrownBy(() -> Money.of(BigDecimal.ONE, "XXXX")).isInstanceOf(BadRequestException.class);
    }
}
