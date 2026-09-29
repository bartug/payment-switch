/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.reconciliation.service;

import com.bartugsevindik.paymentswitch.common.enums.BankCode;
import com.bartugsevindik.paymentswitch.payment.reconciliation.dto.ReconciliationItemDTO;
import com.bartugsevindik.paymentswitch.payment.reconciliation.dto.ReconciliationRunDTO;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * <h1>ReconciliationService</h1>
 * <p>Bizim kayıtlarımızın bankanın gün sonu dosyası ile karşılaştırılması.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 29.09.2026 - PS-7
 */
@Service
public interface ReconciliationService {

    /**
     * <h1>Mutabakat</h1>
     * <p>İki yönlü karşılaştırma yapılır:</p>
     * <ol>
     *     <li><b>Bizden bankaya:</b> o gün onayladığımız satışlar ve başarılı iadeler bankanın dosyasında var mı, tutarı
     *     aynı mı? Bulunamazsa önceki ve sonraki günün dosyasına da bakılır (gün sonu saatine yakın işlemler kayabilir).</li>
     *     <li><b>Bankadan bize:</b> bankanın dosyasındaki her satır bizde karşılığı olan başarılı bir kayıt mı? Bizde hiç
     *     yoksa {@code MISSING_IN_OURS}; varsa ama başarısız / iptal / cevapsız ise {@code STATUS_MISMATCH}.</li>
     * </ol>
     *
     * @param bankCode     Banka
     * @param businessDate İş günü
     * @return Özet ve aksiyon gerektiren satırlar
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 29.09.2026 - PS-7
     */
    ReconciliationRunDTO reconcile(BankCode bankCode, LocalDate businessDate);

    List<ReconciliationItemDTO> getItems(String runId);
}
