/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.bank.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info().title("Payment Switch - Bank Adapter")
                        .description("Banka topic'lerinden gelen istekleri bankalara ileten servis. İşlem durumları ve circuit breaker'lar için admin uç noktaları.")
                        .version("1.0")
                        .contact(new Contact().name("Bartuğ Sevindik").email("bartugsevindik@gmail.com")));
    }
}
