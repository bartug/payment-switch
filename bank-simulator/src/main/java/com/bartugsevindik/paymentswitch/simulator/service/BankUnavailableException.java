/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.simulator.service;

/**
 * Bankanın 503 dönmesi. Controller bu exception'ı HTTP 503'e çevirir.
 */
public class BankUnavailableException extends RuntimeException {

    public BankUnavailableException(String message) {
        super(message);
    }
}
