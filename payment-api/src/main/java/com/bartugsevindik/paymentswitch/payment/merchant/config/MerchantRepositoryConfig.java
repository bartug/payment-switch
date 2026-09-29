/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.merchant.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaRepositories(basePackages = {
        "com.bartugsevindik.paymentswitch.payment.merchant.repository",
        "com.bartugsevindik.paymentswitch.payment.webhook.repository"})
public class MerchantRepositoryConfig {
}
