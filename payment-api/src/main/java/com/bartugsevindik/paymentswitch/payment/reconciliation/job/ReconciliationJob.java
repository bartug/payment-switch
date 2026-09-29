/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.reconciliation.job;

import com.bartugsevindik.paymentswitch.common.enums.BankCode;
import com.bartugsevindik.paymentswitch.payment.operation.config.PaymentOperationProperties;
import com.bartugsevindik.paymentswitch.payment.reconciliation.service.ReconciliationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * <h1>ReconciliationJob</h1>
 * <p>Her gece bir önceki iş gününün mutabakatını tüm bankalar için yapar. Bir bankanın dosyası alınamazsa diğerleri
 * etkilenmez; o banka sonra elle tekrar çalıştırılabilir.</p>
 * <p>Tek pod'da çalışması yeterli; birden fazla pod çalıştırırsa aynı gün için birden fazla çalışma kaydı oluşur
 * (zararsız, sonuçlar aynı). İleride ShedLock ile tek çalışmaya indirilebilir.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 29.09.2026 - PS-7
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReconciliationJob {

    private final ReconciliationService reconciliationService;
    private final PaymentOperationProperties operationProperties;

    @Scheduled(cron = "${application.reconciliation.cron:0 0 3 * * *}", zone = "Europe/Istanbul")
    public void reconcileYesterday() {
        LocalDate yesterday = LocalDate.now(operationProperties.getBusinessZone()).minusDays(1);
        for (BankCode bank : BankCode.values()) {
            try {
                reconciliationService.reconcile(bank, yesterday);
            } catch (RuntimeException e) {
                log.error("Reconciliation failed. bank={}, date={}", bank, yesterday, e);
            }
        }
    }
}
