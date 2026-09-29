/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.ledger.enums;

/**
 * <h1>LedgerEntryType</h1>
 * <p>{@code VOID}: satış kaydının ters kaydı (storno). Muhasebede kayıt silinmez ya da güncellenmez; hata ya da iptal
 * yeni bir ters kayıtla düzeltilir. Geçmiş her zaman izlenebilir kalır.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 29.09.2026 - PS-7
 */
public enum LedgerEntryType {
    SALE,
    REFUND,
    VOID
}
