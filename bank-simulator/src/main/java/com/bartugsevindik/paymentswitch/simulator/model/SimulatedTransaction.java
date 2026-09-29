/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.simulator.model;

import com.bartugsevindik.paymentswitch.simulator.dto.BankTransactionResponse;

import java.time.Instant;

/**
 * Bankanın defterindeki işlem. Simülatör bellekte tutar; yeniden başlatınca silinir.
 */
public record SimulatedTransaction(String orderId, long amount, String currency, String status, String responseCode,
                                   String authCode, String rrn, String message, Instant createdAt) {

    public BankTransactionResponse toResponse() {
        return new BankTransactionResponse(orderId, status, responseCode, authCode, rrn, message);
    }

    public SimulatedTransaction withStatus(String newStatus, String newMessage) {
        return new SimulatedTransaction(orderId, amount, currency, newStatus, responseCode, authCode, rrn, newMessage, createdAt);
    }

    public SimulatedTransaction reversed() {
        return new SimulatedTransaction(orderId, amount, currency, "REVERSED", responseCode, authCode, rrn, "İşlem iptal edildi", createdAt);
    }

    public SimulatedTransaction withAmount(long newAmount) {
        return new SimulatedTransaction(orderId, newAmount, currency, status, responseCode, authCode, rrn, message, createdAt);
    }
}
