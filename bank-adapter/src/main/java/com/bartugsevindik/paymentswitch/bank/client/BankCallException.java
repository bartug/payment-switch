/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.bank.client;

/**
 * Banka çağrısı hatalarının üst sınıfı. Alt sınıflar isteğin bankaya ulaşıp ulaşmadığını ayırt eder.
 */
public abstract class BankCallException extends RuntimeException {

    protected BankCallException(String message, Throwable cause) {
        super(message, cause);
    }
}
