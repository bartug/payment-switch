/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.bank.client;

/**
 * Banka 5xx döndü. Bankanın işlemi yaptıktan sonra mı yapmadan önce mi hata verdiği bilinemez; timeout gibi ele alınır.
 */
public class BankServerErrorException extends BankCallException {

    public BankServerErrorException(String message) {
        super(message, null);
    }
}
