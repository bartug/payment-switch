/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.simulator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * <h1>BankSimulatorApplication</h1>
 * <p>QNB, YKB, Garanti ve İş Bankası sanal POS API'lerini taklit eder. Gecikme ve hata senaryoları buradan yönetilir.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-1
 */
@SpringBootApplication(scanBasePackages = "com.bartugsevindik.paymentswitch")
public class BankSimulatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(BankSimulatorApplication.class, args);
    }
}
