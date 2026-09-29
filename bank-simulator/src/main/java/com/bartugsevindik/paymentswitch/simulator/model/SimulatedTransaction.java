/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.simulator.model;

import com.bartugsevindik.paymentswitch.simulator.dto.BankTransactionResponse;

/**
 * Bankanın defterindeki işlem. Simülatör bellekte tutar; yeniden başlatınca silinir.
 */
public record SimulatedTransaction(String orderId, long amount, String status, String responseCode,
                                   String authCode, String rrn, String message) {

    public BankTransactionResponse toResponse() {
        return new BankTransactionResponse(orderId, status, responseCode, authCode, rrn, message);
    }

    public SimulatedTransaction withStatus(String newStatus, String newMessage) {
        return new SimulatedTransaction(orderId, amount, newStatus, responseCode, authCode, rrn, newMessage);
    }

    public SimulatedTransaction reversed() {
        return new SimulatedTransaction(orderId, amount, "REVERSED", responseCode, authCode, rrn, "İşlem iptal edildi");
    }
}
