/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.routing.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Testlerde kapatılır; outbox relay testlerde elle tetiklenir.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "application.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
