/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.bank.client;

import com.bartugsevindik.paymentswitch.bank.config.BankAdapterProperties;
import com.bartugsevindik.paymentswitch.bank.dto.CardDetails;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * <h1>CardVaultClient</h1>
 * <p>Kart verisini bankaya göndermeden hemen önce payment-api'deki card vault'tan alır.</p>
 * <p>Token yoksa {@link CardDataUnavailableException}; payment-api'ye ulaşılamazsa exception yukarı fırlatılır
 * ve Kafka mesajı tekrar denenir (bu noktada hiçbir şey yazılmamıştır).</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-5
 */
@Component
public class CardVaultClient {

    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private final RestClient restClient;
    private final BankAdapterProperties properties;

    public CardVaultClient(@Qualifier("paymentApiRestClient") RestClient restClient, BankAdapterProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    public CardDetails detokenize(@NotNull String token) {
        try {
            VaultResponse response = restClient.post()
                    .uri("/internal/v1/card-vault/{token}/detokenize", token)
                    .header(INTERNAL_TOKEN_HEADER, properties.getInternalApiToken())
                    .retrieve()
                    .body(VaultResponse.class);
            if (response == null || response.object() == null) {
                throw new CardDataUnavailableException(token);
            }
            return response.object();
        } catch (HttpClientErrorException.NotFound e) {
            throw new CardDataUnavailableException(token);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record VaultResponse(Boolean success, String message, CardDetails object) {
    }
}
