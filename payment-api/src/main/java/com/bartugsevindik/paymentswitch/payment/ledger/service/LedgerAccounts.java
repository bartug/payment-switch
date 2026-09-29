/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.ledger.service;

import com.bartugsevindik.paymentswitch.common.enums.BankCode;

/**
 * <h1>LedgerAccounts</h1>
 * <p>Hesap planı. Aggregator'ın bakış açısıyla:</p>
 * <ul>
 *     <li>{@code BANK_RECEIVABLE:{BANKA}} (varlık): bankanın takas sonrası bize ödeyeceği para.</li>
 *     <li>{@code MERCHANT_PAYABLE:{İŞYERİ}} (borç): bizim üye işyerine ödeyeceğimiz para.</li>
 *     <li>{@code FEE_REVENUE} (gelir): üye işyerinden kesilen komisyon.</li>
 * </ul>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 29.09.2026 - PS-7
 */
public final class LedgerAccounts {

    public static final String FEE_REVENUE = "FEE_REVENUE";

    private LedgerAccounts() {
    }

    public static String bankReceivable(BankCode bank) {
        return "BANK_RECEIVABLE:" + bank.name();
    }

    public static String merchantPayable(String merchantId) {
        return "MERCHANT_PAYABLE:" + merchantId;
    }
}
