/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.idempotency;

import com.bartugsevindik.paymentswitch.payment.controller.PaymentController;
import com.bartugsevindik.paymentswitch.payment.idempotency.repository.IdempotencyRecordRepository;
import com.bartugsevindik.paymentswitch.payment.repository.PaymentRepository;
import com.bartugsevindik.paymentswitch.payment.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IdempotencyIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @Test
    void ayniKeyIleTekrarGelenIstekYeniOdemeOlusturmaz() throws Exception {
        TestTerminal terminal = newTerminal();
        String key = UUID.randomUUID().toString();

        MockHttpServletResponse first = mockMvc.perform(signedPost(terminal, key, VALID_REQUEST))
                .andExpect(status().isAccepted())
                .andExpect(header().string(PaymentController.IDEMPOTENT_REPLAYED_HEADER, "false"))
                .andReturn().getResponse();

        MockHttpServletResponse second = mockMvc.perform(signedPost(terminal, key, VALID_REQUEST))
                .andExpect(status().isAccepted())
                .andExpect(header().string(PaymentController.IDEMPOTENT_REPLAYED_HEADER, "true"))
                .andExpect(jsonPath("$.message").value("Ödeme daha önce alındı."))
                .andReturn().getResponse();

        assertThat(paymentId(second)).isEqualTo(paymentId(first));
        assertThat(idempotencyRecordRepository.findByMerchantIdAndIdempotencyKey(terminal.merchantId(), key)).isPresent();
    }

    @Test
    void alanSirasiVeTutarGosterimiFarkiAyniIstekSayilir() throws Exception {
        TestTerminal terminal = newTerminal();
        String key = UUID.randomUUID().toString();
        mockMvc.perform(signedPost(terminal, key, VALID_REQUEST)).andExpect(status().isAccepted());

        String reordered = """
                {
                  "cvv": "000",
                  "amount": 1250.5,
                  "cardNumber": "5400617020092306",
                  "currency": "TRY",
                  "expiryMonth": "12",
                  "expiryYear": "28",
                  "installmentCount": 3
                }
                """;
        mockMvc.perform(signedPost(terminal, key, reordered))
                .andExpect(status().isAccepted())
                .andExpect(header().string(PaymentController.IDEMPOTENT_REPLAYED_HEADER, "true"));
    }

    @Test
    void ayniKeyFarkliTutar422() throws Exception {
        TestTerminal terminal = newTerminal();
        String key = UUID.randomUUID().toString();
        mockMvc.perform(signedPost(terminal, key, VALID_REQUEST)).andExpect(status().isAccepted());

        mockMvc.perform(signedPost(terminal, key, VALID_REQUEST.replace("1250.50", "9999.00")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message", containsString("farklı bir istek")));
    }

    @Test
    void ayniKeyFarkliKart422() throws Exception {
        TestTerminal terminal = newTerminal();
        String key = UUID.randomUUID().toString();
        mockMvc.perform(signedPost(terminal, key, VALID_REQUEST)).andExpect(status().isAccepted());

        // Kart no WRITE_ONLY olsa da hash'e dahil edilmeli
        mockMvc.perform(signedPost(terminal, key, VALID_REQUEST.replace("5400617020092306", "4111111111111111")))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void farkliUyeIsyerleriAyniKeyiKullanabilir() throws Exception {
        String key = UUID.randomUUID().toString();
        String first = paymentId(mockMvc.perform(signedPost(newTerminal(), key, VALID_REQUEST))
                .andExpect(status().isAccepted()).andReturn().getResponse());
        String second = paymentId(mockMvc.perform(signedPost(newTerminal(), key, VALID_REQUEST))
                .andExpect(status().isAccepted())
                .andExpect(header().string(PaymentController.IDEMPOTENT_REPLAYED_HEADER, "false"))
                .andReturn().getResponse());

        assertThat(second).isNotEqualTo(first);
    }

    @Test
    void keyEksikse400() throws Exception {
        mockMvc.perform(signedPost(newTerminal(), null, VALID_REQUEST))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Idempotency-Key header'ı zorunludur."));
    }

    @Test
    void gecersizFormattakiKey400() throws Exception {
        mockMvc.perform(signedPost(newTerminal(), "kisa", VALID_REQUEST))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("8-64 karakter")));
    }

    @Test
    void esZamanliGelenKopyalardanSadeceBiriOdemeOlusturur() throws Exception {
        TestTerminal terminal = newTerminal();
        String key = UUID.randomUUID().toString();
        int threads = 20;

        CountDownLatch start = new CountDownLatch(1);
        List<MockHttpServletResponse> responses = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
            List<Future<MockHttpServletResponse>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return mockMvc.perform(signedPost(terminal, key, VALID_REQUEST)).andReturn().getResponse();
                }));
            }
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
        assertThat(paymentRepository.findAll().stream().filter(p -> p.getMerchantId().equals(terminal.merchantId()))).hasSize(1);
    }
}
