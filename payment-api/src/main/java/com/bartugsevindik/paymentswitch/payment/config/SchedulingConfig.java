/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Testlerde kapatılır. Cache'lenen her Spring context kendi scheduler'ını çalıştırdığı için
 * açık kalırsa testler birbirinin outbox kayıtlarını gönderir.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "application.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
