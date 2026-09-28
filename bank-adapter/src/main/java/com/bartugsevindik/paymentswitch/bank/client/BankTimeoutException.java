/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.bank.client;

/**
 * İstek gönderildi, cevap gelmedi. Banka işlemi yapmış olabilir; işlem {@code UNKNOWN} olur, <b>retry edilmez</b>.
 */
public class BankTimeoutException extends BankCallException {

    public BankTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
