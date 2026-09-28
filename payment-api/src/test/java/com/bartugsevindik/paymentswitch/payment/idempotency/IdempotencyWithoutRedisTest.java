/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.idempotency;

import com.bartugsevindik.paymentswitch.payment.controller.PaymentController;
import com.bartugsevindik.paymentswitch.payment.idempotency.lock.IdempotencyLock;
import com.bartugsevindik.paymentswitch.payment.idempotency.lock.LockResult;
import com.bartugsevindik.paymentswitch.payment.repository.PaymentRepository;
import com.bartugsevindik.paymentswitch.payment.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Redis çöktüğünde kilit fail-open olur, yani her istek kilidi almış sayılır.
 * Bu durumda da çift ödeme oluşmamalı; DB unique constraint tek başına yeterli olmalı.
 */
class IdempotencyWithoutRedisTest extends AbstractIntegrationTest {

    @MockitoBean
    private IdempotencyLock idempotencyLock;

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    void kilitYokkenDbConstraintCiftOdemeyiEngeller() throws Exception {
        when(idempotencyLock.tryAcquire(anyString(), anyString())).thenReturn(LockResult.SKIPPED);

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

        // Kilit olmadığı için 409 yok; kaybedenler constraint hatası alıp replay döner
        assertThat(responses).allSatisfy(r -> assertThat(r.getStatus()).isEqualTo(202));
        assertThat(responses.stream().map(this::paymentId).distinct()).hasSize(1);
        assertThat(responses).filteredOn(r -> "false".equals(r.getHeader(PaymentController.IDEMPOTENT_REPLAYED_HEADER)))
                .hasSize(1);
        assertThat(paymentRepository.findAll().stream().filter(p -> p.getMerchantId().equals(terminal.merchantId()))).hasSize(1);
    }
}
