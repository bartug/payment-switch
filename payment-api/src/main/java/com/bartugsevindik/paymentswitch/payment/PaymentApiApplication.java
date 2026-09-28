/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * <h1>PaymentApiApplication</h1>
 * <p>POS terminallerinin ödeme gönderdiği giriş noktası. Idempotency, ödeme durum yönetimi
 * ve banka sonuçlarının işlenmesi bu serviste yapılır.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-1
 */
@SpringBootApplication(scanBasePackages = "com.bartugsevindik.paymentswitch")
public class PaymentApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentApiApplication.class, args);
    }
}
