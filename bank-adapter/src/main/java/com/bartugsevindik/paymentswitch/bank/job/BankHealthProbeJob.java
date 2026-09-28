/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.bank.job;

import com.bartugsevindik.paymentswitch.bank.client.BankApiClient;
import com.bartugsevindik.paymentswitch.bank.config.BankAdapterProperties;
import com.bartugsevindik.paymentswitch.bank.health.BankHealthPublisher;
import com.bartugsevindik.paymentswitch.common.enums.BankCode;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * <h1>BankHealthProbeJob</h1>
 * <p>Circuit açılınca routing bankaya işlem göndermeyi bırakır; gerçek trafik gelmediği için circuit'i kapatacak
 * deneme çağrıları da gelmez. Bu job {@code HALF_OPEN} durumdaki bankalara echo (ISO 8583 0800) gönderir;
 * başarılı echo'lar circuit'i kapatır ve banka routing'e geri döner.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-5
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BankHealthProbeJob {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final BankApiClient bankApiClient;
    private final BankHealthPublisher bankHealthPublisher;
    private final BankAdapterProperties properties;

    @Scheduled(fixedDelayString = "${application.bank-adapter.health.probe-interval:5s}")
    public void probe() {
        for (BankCode bank : properties.getBanks()) {
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(bank.name());
            if (circuitBreaker.getState() != CircuitBreaker.State.HALF_OPEN) {
                continue;
            }
            for (int i = 0; i < properties.getCircuitBreaker().getPermittedCallsInHalfOpenState(); i++) {
                try {
                    circuitBreaker.executeRunnable(() -> bankApiClient.echo(bank));
                } catch (RuntimeException e) {
                    log.info("Echo probe failed, circuit stays open. bank={}, cause={}", bank, e.getMessage());
                    break;
                }
            }
        }
    }

    @Scheduled(fixedDelayString = "${application.bank-adapter.health.publish-interval:30s}")
    public void publishAll() {
        bankHealthPublisher.publishAll();
    }
}
