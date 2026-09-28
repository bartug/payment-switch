/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.controller;

import com.bartugsevindik.paymentswitch.payment.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PaymentControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void odemeOlusturulupGetirilir() throws Exception {
        String body = mockMvc.perform(paymentRequest(VALID_REQUEST))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.object.paymentStatus").value("PENDING"))
                .andExpect(jsonPath("$.object.amount").value(1250.50))
                .andExpect(jsonPath("$.object.maskedCardNumber").value("540061******2306"))
                .andExpect(jsonPath("$.object.cardNumber").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(body);
        String paymentId = json.at("/object/paymentId").asText();

        mockMvc.perform(get("/v1/payments/{paymentId}", paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.object.paymentId").value(paymentId))
                .andExpect(jsonPath("$.object.installmentCount").value(3));
    }

    @Test
    void luhnKontroluBasarisizKartReddedilir() throws Exception {
        mockMvc.perform(paymentRequest(VALID_REQUEST.replace("5400617020092306", "5400617020092307")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("cardNumber")));
    }

    @Test
    void sanalPosCvvsizReddedilir() throws Exception {
        mockMvc.perform(paymentRequest(VALID_REQUEST.replace("\"cvv\": \"000\"", "\"cvv\": null")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Sanal POS işlemlerinde CVV zorunludur."));
    }

    @Test
    void olmayanOdeme404() throws Exception {
        mockMvc.perform(get("/v1/payments/{paymentId}", "yok"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    private MockHttpServletRequestBuilder paymentRequest(String content) {
        return post("/v1/payments")
                .header(PaymentController.IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(content);
    }
}
