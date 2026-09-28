/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.bank.client;

/**
 * Banka isteği 4xx ile reddetti (hatalı format, yetkisiz). İstek işlenmemiştir; circuit'i açmaz.
 */
public class BankClientErrorException extends BankCallException {

    public BankClientErrorException(String message) {
        super(message, null);
    }
}
