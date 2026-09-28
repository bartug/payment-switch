/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.bank.client;

/**
 * Bağlantı kurulamadı. İstek bankaya <b>kesinlikle ulaşmadı</b>; işlem güvenle başarısız sayılabilir.
 */
public class BankConnectException extends BankCallException {

    public BankConnectException(String message, Throwable cause) {
        super(message, cause);
    }
}
