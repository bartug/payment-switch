/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.operation.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableConfigurationProperties(PaymentOperationProperties.class)
@EnableJpaRepositories(basePackages = {"com.bartugsevindik.paymentswitch.payment.operation.repository"})
public class PaymentOperationConfig {
}
