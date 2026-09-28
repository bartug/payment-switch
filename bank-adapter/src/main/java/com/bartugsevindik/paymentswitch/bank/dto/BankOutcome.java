/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.bank.dto;

import com.bartugsevindik.paymentswitch.bank.enums.BankTransactionStatus;

/**
 * <h1>BankOutcome</h1>
 * <p>Banka çağrısının sonucu, adapter'ın kendi modelinde.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-5
 */
public record BankOutcome(BankTransactionStatus status, String responseCode, String authCode, String rrn, String message) {

    public static BankOutcome from(BankApiResponse response) {
        BankTransactionStatus status = switch (response.status()) {
            case "APPROVED" -> BankTransactionStatus.APPROVED;
            case "REVERSED" -> BankTransactionStatus.REVERSED;
            default -> BankTransactionStatus.DECLINED;
        };
        return new BankOutcome(status, response.responseCode(), response.authCode(), response.rrn(), response.message());
    }

    public static BankOutcome unknown(String reason) {
        return new BankOutcome(BankTransactionStatus.UNKNOWN, null, null, null, reason);
    }

    public static BankOutcome notSent(String reason) {
        return new BankOutcome(BankTransactionStatus.FAILED, null, null, null, reason);
    }

    public static BankOutcome reversed(BankApiResponse response) {
        return new BankOutcome(BankTransactionStatus.REVERSED, response.responseCode(), null, response.rrn(),
                "Cevapsız kalan işlem bankada iptal edildi.");
    }
}
