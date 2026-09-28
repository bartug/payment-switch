/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.idempotency.job;

import com.bartugsevindik.paymentswitch.payment.idempotency.service.IdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * <h1>IdempotencyCleanupJob</h1>
 * <p>Süresi dolan idempotency kayıtlarını siler. Birden fazla pod aynı anda çalıştırsa da sorun olmaz,
 * her çalışma sadece o an süresi dolmuş kayıtları siler.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-2
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotencyCleanupJob {

    private static final int MAX_BATCHES_PER_RUN = 50;

    private final IdempotencyService idempotencyService;

    @Scheduled(cron = "${application.idempotency.cleanup-cron:0 */10 * * * *}")
    public void cleanup() {
        int total = 0;
        for (int i = 0; i < MAX_BATCHES_PER_RUN; i++) {
            int deleted = idempotencyService.deleteExpiredRecords();
            total += deleted;
            if (deleted == 0) {
                break;
            }
        }
        if (total > 0) {
            log.info("Expired idempotency records deleted. count={}", total);
        }
    }
}
