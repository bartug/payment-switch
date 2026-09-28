/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.enums;

import com.bartugsevindik.paymentswitch.payment.entity.Payment;
import com.bartugsevindik.paymentswitch.payment.exception.InvalidPaymentStateException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentStatusTest {

    @Test
    void basariliAkis() {
        Payment payment = new Payment();
        payment.changeStatus(PaymentStatus.PENDING);
        payment.changeStatus(PaymentStatus.ROUTED);
        payment.changeStatus(PaymentStatus.APPROVED);

        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    void cevapsizIslemReversalIleKapanir() {
        assertThat(PaymentStatus.ROUTED.canTransitionTo(PaymentStatus.UNKNOWN)).isTrue();
        assertThat(PaymentStatus.UNKNOWN.canTransitionTo(PaymentStatus.REVERSED)).isTrue();
    }

    @Test
    void reddedilenIslemOnaylanamaz() {
        Payment payment = new Payment();
        payment.changeStatus(PaymentStatus.PENDING);
        payment.changeStatus(PaymentStatus.ROUTED);
        payment.changeStatus(PaymentStatus.DECLINED);

        assertThatThrownBy(() -> payment.changeStatus(PaymentStatus.APPROVED))
                .isInstanceOf(InvalidPaymentStateException.class);
    }

    @Test
    void bankaSonucuRoutingSonucundanOnceGelebilir() {
        assertThat(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.APPROVED)).isTrue();
    }

    @Test
    void cevapsizIslemBasarisizaDusemez() {
        // UNKNOWN'da para çekilmiş olabilir; ya onaylanır ya reversal ile geri alınır
        assertThat(PaymentStatus.UNKNOWN.canTransitionTo(PaymentStatus.FAILED)).isFalse();
    }

    @Test
    void bankayaGitmemisIslemIadeEdilemez() {
        assertThat(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.REFUNDED)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"DECLINED", "REVERSED", "VOIDED", "REFUNDED", "FAILED"})
    void sonDurumlardanCikilmaz(PaymentStatus status) {
        assertThat(status.isFinal()).isTrue();
    }
}
