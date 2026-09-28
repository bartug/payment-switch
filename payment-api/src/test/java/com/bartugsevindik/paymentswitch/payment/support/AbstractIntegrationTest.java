/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.support;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Container'lar tüm test sınıfları için bir kez açılır (singleton container pattern).
 * Her sınıfta yeniden açılsaydı Spring context cache'i de işe yaramazdı.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static {
        POSTGRES.start();
        REDIS.start();
    }

    protected static final String VALID_REQUEST = """
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
}
