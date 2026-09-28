/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.bank.config;

import com.bartugsevindik.paymentswitch.common.enums.BankCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/**
 * <h1>BankAdapterProperties</h1>
 * <p>{@code application.bank-adapter.*} ayarları.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-5
 */
@Data
@Validated
@ConfigurationProperties(prefix = "application.bank-adapter")
public class BankAdapterProperties {

    /**
     * Bu süreçte işlem gönderilen bankalar. Her banka kendi listener container'ı, circuit breaker'ı ve bulkhead'i ile çalışır.
     */
    @NotEmpty
    private List<BankCode> banks;

    private int consumerConcurrency = 2;

    @NotBlank
    private String bankApiUrl;

    @NotBlank
    private String paymentApiUrl;

    @NotBlank
    private String internalApiToken;

    @NotNull
    private Duration connectTimeout = Duration.ofSeconds(1);

    /**
     * Banka bu sürede cevap vermezse işlem {@code UNKNOWN} olur.
     */
    @NotNull
    private Duration readTimeout = Duration.ofSeconds(5);

    private Recovery recovery = new Recovery();
    private CircuitBreaker circuitBreaker = new CircuitBreaker();
    private Bulkhead bulkhead = new Bulkhead();
    private Health health = new Health();

    @Data
    public static class Recovery {
        /**
         * {@code SENDING} durumunda bu süreden uzun kalan işlem, uygulama banka cevabını beklerken çökmüş demektir.
         */
        private Duration stuckAfter = Duration.ofSeconds(30);
        private Duration inquiryInitialDelay = Duration.ofSeconds(2);
        private Duration inquiryBackoff = Duration.ofSeconds(2);
        private int maxInquiryAttempts = 5;
        private int maxReversalAttempts = 10;
        private int batchSize = 50;
    }

    @Data
    public static class CircuitBreaker {
        private float failureRateThreshold = 50;
        private int minimumNumberOfCalls = 5;
        private int slidingWindowSize = 10;
        private Duration waitDurationInOpenState = Duration.ofSeconds(10);
        private int permittedCallsInHalfOpenState = 3;
    }

    @Data
    public static class Bulkhead {
        private int maxConcurrentCalls = 20;
        private Duration maxWait = Duration.ofSeconds(1);
    }

    @Data
    public static class Health {
        private Duration probeInterval = Duration.ofSeconds(5);
        private Duration publishInterval = Duration.ofSeconds(30);
    }
}
