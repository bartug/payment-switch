/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.bank.job;

import com.bartugsevindik.paymentswitch.bank.service.RecoveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * <h1>RecoveryJob</h1>
 * <p>Cevapsız kalan işlemleri netleştirir. Birden fazla pod çalıştırabilir; işler {@code SKIP LOCKED} ile paylaşılır.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-5
 */
@Component
@RequiredArgsConstructor
public class RecoveryJob {

    private final RecoveryService recoveryService;

    @Scheduled(fixedDelayString = "${application.bank-adapter.recovery.interval:1s}")
    public void run() {
        recoveryService.runOnce();
    }
}
