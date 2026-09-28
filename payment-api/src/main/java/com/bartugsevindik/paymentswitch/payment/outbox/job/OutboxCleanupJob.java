/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.outbox.job;

import com.bartugsevindik.paymentswitch.payment.outbox.service.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * <h1>OutboxCleanupJob</h1>
 * <p>Gönderilmiş ve saklama süresi dolmuş outbox kayıtlarını siler. Gönderilmemiş kayıtlara dokunmaz.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-3
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxCleanupJob {

    private static final int MAX_BATCHES_PER_RUN = 50;

    private final OutboxService outboxService;

    @Scheduled(cron = "${application.outbox.cleanup-cron:0 15 * * * *}")
    public void cleanup() {
        int total = 0;
        for (int i = 0; i < MAX_BATCHES_PER_RUN; i++) {
            int deleted = outboxService.deletePublishedEvents();
            total += deleted;
            if (deleted == 0) {
                break;
            }
        }
        if (total > 0) {
            log.info("Published outbox events deleted. count={}", total);
        }
    }
}
