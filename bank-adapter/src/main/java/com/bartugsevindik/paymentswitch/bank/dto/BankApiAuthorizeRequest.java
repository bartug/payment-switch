/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.bank.dto;

/**
 * Bankanın satış isteği formatı.
 */
public record BankApiAuthorizeRequest(String orderId, String pan, String expiryMonth, String expiryYear, String cvv,
                                      long amount, String currency, int installmentCount) {

    @Override
    public String toString() {
        return "BankApiAuthorizeRequest[orderId=" + orderId + ", amount=" + amount + "]";
    }
}
