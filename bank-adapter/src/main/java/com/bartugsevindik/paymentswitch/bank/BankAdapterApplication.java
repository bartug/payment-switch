/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.bank;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * <h1>BankAdapterApplication</h1>
 * <p>Banka topic'inden okuduğu istekleri bankanın protokolüne çevirip gönderir. Her banka için ayrı deployment olarak çalışır.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-1
 */
@SpringBootApplication(scanBasePackages = "com.bartugsevindik.paymentswitch")
public class BankAdapterApplication {

    public static void main(String[] args) {
        SpringApplication.run(BankAdapterApplication.class, args);
    }
}
