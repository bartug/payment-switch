/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.controller;

import com.bartugsevindik.paymentswitch.common.enums.TerminalType;
import com.bartugsevindik.paymentswitch.payment.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PaymentControllerTest extends AbstractIntegrationTest {

    @Test
    void odemeOlusturulupGetirilir() throws Exception {
        TestTerminal terminal = newTerminal();

        String paymentId = paymentId(mockMvc.perform(signedPost(terminal, UUID.randomUUID().toString(), VALID_REQUEST))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.object.paymentStatus").value("PENDING"))
                .andExpect(jsonPath("$.object.amount").value(1250.50))
                .andExpect(jsonPath("$.object.merchantId").value(terminal.merchantId()))
                .andExpect(jsonPath("$.object.terminalId").value(terminal.terminalId()))
                .andExpect(jsonPath("$.object.maskedCardNumber").value("540061******2306"))
                .andExpect(jsonPath("$.object.cardNumber").doesNotExist())
                .andReturn().getResponse());

        mockMvc.perform(signedGet(terminal, "/v1/payments/" + paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.object.paymentId").value(paymentId))
                .andExpect(jsonPath("$.object.installmentCount").value(3));
    }

    @Test
    void luhnKontroluBasarisizKartReddedilir() throws Exception {
        mockMvc.perform(signedPost(newTerminal(), UUID.randomUUID().toString(),
                        VALID_REQUEST.replace("5400617020092306", "5400617020092307")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("cardNumber")));
    }

    @Test
    void sanalPosCvvsizReddedilir() throws Exception {
        mockMvc.perform(signedPost(newTerminal(TerminalType.VIRTUAL), UUID.randomUUID().toString(),
                        VALID_REQUEST.replace("\"cvv\": \"000\"", "\"cvv\": null")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Sanal POS işlemlerinde CVV zorunludur."));
    }

    @Test
    void fizikiPosCvvsizKabulEdilir() throws Exception {
        mockMvc.perform(signedPost(newTerminal(TerminalType.PHYSICAL), UUID.randomUUID().toString(),
                        VALID_REQUEST.replace("\"cvv\": \"000\"", "\"cvv\": null")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.object.terminalType").value("PHYSICAL"));
    }

    @Test
    void olmayanOdeme404() throws Exception {
        mockMvc.perform(signedGet(newTerminal(), "/v1/payments/yok"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }
}
