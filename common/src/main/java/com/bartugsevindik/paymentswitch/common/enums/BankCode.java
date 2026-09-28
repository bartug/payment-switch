/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * <h1>BankCode</h1>
 * <p>Switch'in işlem gönderebildiği acquirer bankalar. {@code eftCode} TCMB banka kodudur.</p>
 * <p>Kafka topic isimleri enum adından üretilir: {@code bank.requests.YKB}</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-1
 */
@Getter
@RequiredArgsConstructor
public enum BankCode {
    QNB("0111", "QNB Finansbank"),
    YKB("0067", "Yapı Kredi"),
    GARANTI("0062", "Garanti BBVA"),
    ISBANK("0064", "Türkiye İş Bankası"),
    AKBANK("0046", "Akbank");

    private final String eftCode;
    private final String displayName;

    public String requestTopic() {
        return "bank.requests." + name();
    }
}
