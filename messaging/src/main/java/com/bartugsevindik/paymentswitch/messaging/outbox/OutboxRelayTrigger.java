/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.messaging.outbox;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * <h1>OutboxRelayTrigger</h1>
 * <p>Outbox'a yazan transaction commit olduğunda relay'i hemen çalıştırır; event bir sonraki polling'i beklemez.</p>
 * <p>Yük testinde bir ödemenin uçtan uca süresinin %70'inin üç outbox'ta polling beklemesiyle geçtiği ölçüldü
 * (200 ms × 3 geçiş). Polling kaldırılmaz: başka pod'un commit'i, Kafka'nın kapalı olduğu anlar ve bu pod'un
 * uyandırma sırasında ölmesi için yedektir.</p>
 * <p>Arka arkaya gelen commit'ler tek çalışmada birleştirilir: çalışan bir relay varken en fazla bir çalışma daha
 * sıraya girer. 1000 commit 1000 relay çalıştırmaz.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 29.09.2026 - PS-8
 */
@Slf4j
public class OutboxRelayTrigger implements DisposableBean {

    private final ExecutorService executor = Executors.newSingleThreadExecutor(Thread.ofVirtual().name("outbox-wakeup").factory());
    private final AtomicBoolean scheduled = new AtomicBoolean(false);
    private final boolean enabled;
    private OutboxRelay relay;

    public OutboxRelayTrigger(boolean enabled) {
        this.enabled = enabled;
    }

    public void setRelay(OutboxRelay relay) {
        this.relay = relay;
    }

    public void wakeUp() {
        if (!enabled || relay == null || !scheduled.compareAndSet(false, true)) {
            return;
        }
        executor.execute(() -> {
            // Bayrak relay başlamadan indirilir: çalışırken gelen commit bir sonraki çalışmayı garanti eder
            scheduled.set(false);
            try {
                relay.publishPending();
            } catch (RuntimeException e) {
                log.warn("Outbox wake-up publish failed, scheduled polling will retry", e);
            }
        });
    }

    @Override
    public void destroy() {
        executor.shutdown();
    }
}
