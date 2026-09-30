/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.bank.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class HttpClientConfig {

    /**
     * Connect ve read timeout ayrı tutulur: bağlantı kurulamadıysa istek bankaya hiç ulaşmamıştır (güvenle FAILED),
     * cevap gelmediyse banka işlemi yapmış olabilir (UNKNOWN).
     */
    /**
     * Boot'un {@code RestClient.Builder}'ı observation ile gelir: her banka çağrısı trace'te span olur, traceparent
     * header'ı eklenir ve {@code http.client.requests} metriği üretilir.
     */
    @Bean
    public RestClient bankRestClient(RestClient.Builder builder, BankAdapterProperties properties) {
        return builder.clone()
                .baseUrl(properties.getBankApiUrl())
                .requestFactory(requestFactory(properties.getConnectTimeout(), properties.getReadTimeout()))
                .build();
    }

    @Bean
    public RestClient paymentApiRestClient(RestClient.Builder builder, BankAdapterProperties properties) {
        return builder.clone()
                .baseUrl(properties.getPaymentApiUrl())
                .requestFactory(requestFactory(properties.getConnectTimeout(), Duration.ofSeconds(3)))
                .build();
    }

    private static JdkClientHttpRequestFactory requestFactory(Duration connectTimeout, Duration readTimeout) {
        // JDK HttpClient http:// adreslerde h2c upgrade dener; body'li POST'larda bazı sunucularda bağlantı bozulur ve
        // bankaya ulaşmış bir istek gereksiz yere UNKNOWN olur. Banka entegrasyonlarında protokol sabitlenir.
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(connectTimeout)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(readTimeout);
        return factory;
    }
}
