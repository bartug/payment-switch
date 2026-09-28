/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.bank.config;

import com.bartugsevindik.paymentswitch.bank.client.BankConnectException;
import com.bartugsevindik.paymentswitch.bank.client.BankServerErrorException;
import com.bartugsevindik.paymentswitch.bank.client.BankTimeoutException;
import com.bartugsevindik.paymentswitch.common.enums.BankCode;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedBulkheadMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Her banka için ayrı circuit breaker ve bulkhead. Bir bankanın yavaşlaması ya da çökmesi diğer bankaların
 * thread'lerini ve bağlantılarını tüketmez.
 */
@Configuration
public class ResilienceConfig {

    /**
     * Sadece altyapı hataları (bağlantı, timeout, 5xx) circuit'i açar. Bankanın reddetmesi (51 yetersiz bakiye)
     * başarılı bir çağrıdır; aksi halde bakiyesi yetmeyen kart sahipleri bankayı devre dışı bırakırdı.
     */
    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry(BankAdapterProperties properties, MeterRegistry meterRegistry) {
        BankAdapterProperties.CircuitBreaker cb = properties.getCircuitBreaker();
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(cb.getSlidingWindowSize())
                .minimumNumberOfCalls(cb.getMinimumNumberOfCalls())
                .failureRateThreshold(cb.getFailureRateThreshold())
                .waitDurationInOpenState(cb.getWaitDurationInOpenState())
                .permittedNumberOfCallsInHalfOpenState(cb.getPermittedCallsInHalfOpenState())
                // Bekleme süresi dolunca kendiliğinden HALF_OPEN'a geçer; echo probu deneme çağrılarını yapar
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .recordExceptions(BankConnectException.class, BankTimeoutException.class, BankServerErrorException.class)
                .build();
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        for (BankCode bank : properties.getBanks()) {
            registry.circuitBreaker(bank.name());
        }
        TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry).bindTo(meterRegistry);
        return registry;
    }

    @Bean
    public BulkheadRegistry bulkheadRegistry(BankAdapterProperties properties, MeterRegistry meterRegistry) {
        BulkheadConfig config = BulkheadConfig.custom()
                .maxConcurrentCalls(properties.getBulkhead().getMaxConcurrentCalls())
                .maxWaitDuration(properties.getBulkhead().getMaxWait())
                .build();
        BulkheadRegistry registry = BulkheadRegistry.of(config);
        for (BankCode bank : properties.getBanks()) {
            registry.bulkhead(bank.name());
        }
        TaggedBulkheadMetrics.ofBulkheadRegistry(registry).bindTo(meterRegistry);
        return registry;
    }
}
