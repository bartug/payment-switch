/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.routing.config;

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
                .info(new Info().title("Payment Switch - Routing Service")
                        .description("Ödemeleri BIN ve kurallara göre bankalara dağıtan servis. Routing kararlarını denemek ve bankaları yönetmek için admin uç noktaları.")
                        .version("1.0")
                        .contact(new Contact().name("Bartuğ Sevindik").email("bartugsevindik@gmail.com")));
    }
}
