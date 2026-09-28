/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.config;

import com.bartugsevindik.paymentswitch.payment.terminal.security.TerminalAuthenticationFilter;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    private static final String SIGNED_PATH_PREFIX = "/v1/payments";

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info().title("Payment Switch - Payment API")
                        .description("""
                                POS terminallerinden gelen ödemeleri karşılayan API.

                                `/v1/payments` altındaki istekler terminal secret'ı ile **HMAC-SHA256** imzalanmalıdır.
                                İmza detayları: `docs/03-terminal-kimlik-dogrulama.md`.
                                Swagger'dan denemek için `scripts/pos-request.sh` ile üretilen header'lar kullanılabilir.
                                """)
                        .version("1.0")
                        .contact(new Contact().name("Bartuğ Sevindik").email("bartugsevindik@gmail.com")));
    }

    /**
     * İmza header'ları filter'da okunduğu için controller'da görünmez; dokümana buradan eklenir.
     */
    @Bean
    public OpenApiCustomizer terminalSignatureHeaders() {
        return openApi -> openApi.getPaths().forEach((path, item) -> {
            if (!path.startsWith(SIGNED_PATH_PREFIX)) {
                return;
            }
            item.readOperations().forEach(operation -> operation
                    .addParametersItem(header(TerminalAuthenticationFilter.TERMINAL_ID_HEADER, "Terminal numarası", "TRM00000001"))
                    .addParametersItem(header(TerminalAuthenticationFilter.TIMESTAMP_HEADER, "Unix epoch saniye. Sunucu saatinden en fazla 5 dk farklı olabilir.", "1790582400"))
                    .addParametersItem(header(TerminalAuthenticationFilter.SIGNATURE_HEADER, "Base64(HMAC-SHA256(secret, stringToSign))", "")));
        });
    }

    private static HeaderParameter header(String name, String description, String example) {
        HeaderParameter parameter = new HeaderParameter();
        parameter.setName(name);
        parameter.setDescription(description);
        parameter.setRequired(true);
        parameter.setSchema(new StringSchema());
        parameter.setExample(example);
        return parameter;
    }
}
