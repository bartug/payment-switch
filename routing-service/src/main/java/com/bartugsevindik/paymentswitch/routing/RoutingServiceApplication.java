/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.routing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * <h1>RoutingServiceApplication</h1>
 * <p>Gelen ödemeyi BIN tablosu ve routing kurallarına göre ilgili banka topic'ine yönlendirir. Detaylar PS-4 ile gelecek.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-1
 */
@SpringBootApplication(scanBasePackages = "com.bartugsevindik.paymentswitch")
public class RoutingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RoutingServiceApplication.class, args);
    }
}
