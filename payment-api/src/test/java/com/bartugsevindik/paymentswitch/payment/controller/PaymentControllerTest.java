/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PaymentControllerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String VALID_REQUEST = """
            {
              "merchantId": "MRC0000001",
              "terminalId": "TRM00000001",
              "terminalType": "VIRTUAL",
              "amount": 1250.50,
              "currency": "TRY",
              "installmentCount": 3,
              "cardNumber": "5400617020092306",
              "expiryMonth": "12",
              "expiryYear": "28",
              "cvv": "000"
            }
            """;

    @Test
    void odemeOlusturulupGetirilir() throws Exception {
        String body = mockMvc.perform(post("/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
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
        mockMvc.perform(post("/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST.replace("5400617020092306", "5400617020092307")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("cardNumber")));
    }

    @Test
    void sanalPosCvvsizReddedilir() throws Exception {
        mockMvc.perform(post("/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST.replace("\"cvv\": \"000\"", "\"cvv\": null")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Sanal POS işlemlerinde CVV zorunludur."));
    }

    @Test
    void olmayanOdeme404() throws Exception {
        mockMvc.perform(get("/v1/payments/{paymentId}", "yok"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }
}
