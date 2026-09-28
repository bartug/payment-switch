/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.bank.dto;

/**
 * <h1>CardDetails</h1>
 * <p>Card vault'tan alınan kart verisi. Sadece bellekte, banka çağrısı süresince yaşar; DB'ye ve log'a yazılmaz.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-5
 */
public record CardDetails(String pan, String expiryMonth, String expiryYear, String cvv) {

    @Override
    public String toString() {
        return "CardDetails[pan=" + pan.substring(0, 6) + "******" + pan.substring(pan.length() - 4) + ", cvv=***]";
    }
}
