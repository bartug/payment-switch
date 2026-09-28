/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.idempotency;

import com.bartugsevindik.paymentswitch.payment.controller.PaymentController;
import com.bartugsevindik.paymentswitch.payment.idempotency.repository.IdempotencyRecordRepository;
import com.bartugsevindik.paymentswitch.payment.repository.PaymentRepository;
import com.bartugsevindik.paymentswitch.payment.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IdempotencyIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @Test
    void ayniKeyIleTekrarGelenIstekYeniOdemeOlusturmaz() throws Exception {
        String key = UUID.randomUUID().toString();

        MockHttpServletResponse first = mockMvc.perform(paymentRequest(key, VALID_REQUEST))
                .andExpect(status().isAccepted())
                .andExpect(header().string(PaymentController.IDEMPOTENT_REPLAYED_HEADER, "false"))
                .andReturn().getResponse();

        MockHttpServletResponse second = mockMvc.perform(paymentRequest(key, VALID_REQUEST))
                .andExpect(status().isAccepted())
                .andExpect(header().string(PaymentController.IDEMPOTENT_REPLAYED_HEADER, "true"))
                .andExpect(jsonPath("$.message").value("Ödeme daha önce alındı."))
                .andReturn().getResponse();

        assertThat(paymentId(second)).isEqualTo(paymentId(first));
        assertThat(idempotencyRecordRepository.findByMerchantIdAndIdempotencyKey("MRC0000001", key)).isPresent();
    }

    @Test
    void alanSirasiVeTutarGosterimiFarkiAyniIstekSayilir() throws Exception {
        String key = UUID.randomUUID().toString();
        mockMvc.perform(paymentRequest(key, VALID_REQUEST)).andExpect(status().isAccepted());

        String reordered = """
                {
                  "cvv": "000",
                  "amount": 1250.5,
                  "cardNumber": "5400617020092306",
                  "currency": "TRY",
                  "expiryMonth": "12",
                  "expiryYear": "28",
                  "installmentCount": 3,
                  "merchantId": "MRC0000001",
                  "terminalId": "TRM00000001",
                  "terminalType": "VIRTUAL"
                }
                """;
        mockMvc.perform(paymentRequest(key, reordered))
                .andExpect(status().isAccepted())
                .andExpect(header().string(PaymentController.IDEMPOTENT_REPLAYED_HEADER, "true"));
    }

    @Test
    void ayniKeyFarkliTutar422() throws Exception {
        String key = UUID.randomUUID().toString();
        mockMvc.perform(paymentRequest(key, VALID_REQUEST)).andExpect(status().isAccepted());

        mockMvc.perform(paymentRequest(key, VALID_REQUEST.replace("1250.50", "9999.00")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message", containsString("farklı bir istek")));
    }

    @Test
    void ayniKeyFarkliKart422() throws Exception {
        String key = UUID.randomUUID().toString();
        mockMvc.perform(paymentRequest(key, VALID_REQUEST)).andExpect(status().isAccepted());

        // Kart no WRITE_ONLY olsa da hash'e dahil edilmeli
        mockMvc.perform(paymentRequest(key, VALID_REQUEST.replace("5400617020092306", "4111111111111111")))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void farkliUyeIsyerleriAyniKeyiKullanabilir() throws Exception {
        String key = UUID.randomUUID().toString();
        String first = paymentId(mockMvc.perform(paymentRequest(key, VALID_REQUEST))
                .andExpect(status().isAccepted()).andReturn().getResponse());
        String second = paymentId(mockMvc.perform(paymentRequest(key, VALID_REQUEST.replace("MRC0000001", "MRC0000002")))
                .andExpect(status().isAccepted())
                .andExpect(header().string(PaymentController.IDEMPOTENT_REPLAYED_HEADER, "false"))
                .andReturn().getResponse());

        assertThat(second).isNotEqualTo(first);
    }

    @Test
    void keyEksikse400() throws Exception {
        mockMvc.perform(post("/v1/payments").contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Idempotency-Key header'ı zorunludur."));
    }

    @Test
    void gecersizFormattakiKey400() throws Exception {
        mockMvc.perform(paymentRequest("kisa", VALID_REQUEST))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("8-64 karakter")));
    }

    @Test
    void esZamanliGelenKopyalardanSadeceBiriOdemeOlusturur() throws Exception {
        String key = UUID.randomUUID().toString();
        String merchantId = "MRC" + key.substring(0, 7);
        String body = VALID_REQUEST.replace("MRC0000001", merchantId);
        int threads = 20;

        CountDownLatch start = new CountDownLatch(1);
        List<Callable<MockHttpServletResponse>> tasks = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            tasks.add(() -> {
                start.await();
                return mockMvc.perform(paymentRequest(key, body)).andReturn().getResponse();
            });
        }

        List<MockHttpServletResponse> responses = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
            List<Future<MockHttpServletResponse>> futures = tasks.stream().map(executor::submit).toList();
            start.countDown();
            for (Future<MockHttpServletResponse> future : futures) {
                responses.add(future.get());
            }
        }

        // Her cevap ya 202 (yeni ya da replay) ya da 409 (işleniyor) olmalı; 500 olmamalı
        assertThat(responses).allSatisfy(r -> assertThat(r.getStatus()).isIn(202, 409));
        assertThat(responses).filteredOn(r -> r.getStatus() == 409)
                .allSatisfy(r -> assertThat(r.getHeader("Retry-After")).isEqualTo("1"));

        Set<String> paymentIds = responses.stream()
                .filter(r -> r.getStatus() == 202)
                .map(this::paymentId)
                .collect(Collectors.toSet());
        assertThat(paymentIds).hasSize(1);
        assertThat(paymentRepository.findAll().stream().filter(p -> p.getMerchantId().equals(merchantId))).hasSize(1);
    }

    private MockHttpServletRequestBuilder paymentRequest(String key, String content) {
        return post("/v1/payments")
                .header(PaymentController.IDEMPOTENCY_KEY_HEADER, key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(content);
    }

    private String paymentId(MockHttpServletResponse response) {
        try {
            return objectMapper.readTree(response.getContentAsString()).at("/object/paymentId").asText();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
