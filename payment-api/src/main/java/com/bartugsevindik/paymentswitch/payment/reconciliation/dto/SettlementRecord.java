/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.reconciliation.dto;

import com.bartugsevindik.paymentswitch.payment.reconciliation.enums.SettlementRecordType;

import java.time.LocalDate;

/**
 * <h1>SettlementRecord</h1>
 * <p>Bankanın gün sonu dosyasındaki bir satır.</p>
 *
 * @param fileDate Satırın bulunduğu dosyanın tarihi
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 29.09.2026 - PS-7
 */
public record SettlementRecord(String orderId, SettlementRecordType type, long amount, String currency, String rrn,
                               String authCode, String operationId, LocalDate fileDate) {

    public String key() {
        return type == SettlementRecordType.SALE ? "SALE:" + orderId : "REFUND:" + operationId;
    }
}
