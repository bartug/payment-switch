/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.vault.job;

import com.bartugsevindik.paymentswitch.payment.vault.service.CardVaultService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * <h1>CardVaultCleanupJob</h1>
 * <p>Süresi dolan kart verilerini siler. Normalde kart verisi banka cevabıyla birlikte silinir; bu job bankaya
 * hiç ulaşamayan ödemeler içindir.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-5
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CardVaultCleanupJob {

    private final CardVaultService cardVaultService;

    @Scheduled(cron = "${application.card-vault.cleanup-cron:0 * * * * *}")
    public void cleanup() {
        int deleted = cardVaultService.deleteExpired();
        if (deleted > 0) {
            log.info("Expired card data deleted. count={}", deleted);
        }
    }
}
