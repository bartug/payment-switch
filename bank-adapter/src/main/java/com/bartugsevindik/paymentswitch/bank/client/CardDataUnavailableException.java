/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.bank.client;

/**
 * Card vault'ta token yok ya da süresi dolmuş. Tekrar denemek işe yaramaz; işlem bankaya gönderilmeden başarısız olur.
 */
public class CardDataUnavailableException extends RuntimeException {

    public CardDataUnavailableException(String token) {
        super("Card data not found or expired. token=" + token);
    }
}
