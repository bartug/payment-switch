/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.bank.client;

import com.bartugsevindik.paymentswitch.bank.dto.BankApiAuthorizeRequest;
import com.bartugsevindik.paymentswitch.bank.dto.BankApiResponse;
import com.bartugsevindik.paymentswitch.common.enums.BankCode;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.ConnectException;
import java.net.http.HttpConnectTimeoutException;
import java.nio.channels.UnresolvedAddressException;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * <h1>BankApiClient</h1>
 * <p>Bankaların sanal POS API'si ile konuşur ve ağ hatalarını <b>isteğin bankaya ulaşıp ulaşmadığına</b> göre ayırır:
 * bağlantı hatası → {@link BankConnectException}, cevap alınamadı → {@link BankTimeoutException},
 * 5xx → {@link BankServerErrorException}, 4xx → {@link BankClientErrorException}.</p>
 * <p>Retry yoktur. Satış isteğinin tekrarı çift çekim demektir; kararı çağıran taraf verir.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-5
 */
@Slf4j
@Component
public class BankApiClient {

    private final RestClient restClient;

    public BankApiClient(@Qualifier("bankRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public BankApiResponse authorize(@NotNull BankCode bank, @NotNull BankApiAuthorizeRequest request) {
        return call(bank, "authorize", () -> restClient.post()
                .uri("/banks/{bank}/v1/authorize", bank)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(BankApiResponse.class));
    }

    /**
     * @return İşlem. Banka işlemi hiç almadıysa boş.
     */
    public Optional<BankApiResponse> inquire(@NotNull BankCode bank, @NotNull String orderId) {
        try {
            return Optional.ofNullable(call(bank, "inquiry", () -> restClient.get()
                    .uri("/banks/{bank}/v1/transactions/{orderId}", bank, orderId)
                    .retrieve()
                    .body(BankApiResponse.class)));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }

    public BankApiResponse reverse(@NotNull BankCode bank, @NotNull String orderId) {
        return call(bank, "reversal", () -> restClient.post()
                .uri("/banks/{bank}/v1/transactions/{orderId}/reversal", bank, orderId)
                .retrieve()
                .body(BankApiResponse.class));
    }

    public void echo(@NotNull BankCode bank) {
        call(bank, "echo", () -> restClient.get()
                .uri("/banks/{bank}/v1/echo", bank)
                .retrieve()
                .toBodilessEntity());
    }

    private <T> T call(BankCode bank, String operation, Supplier<T> request) {
        try {
            return request.get();
        } catch (HttpServerErrorException e) {
            throw new BankServerErrorException(bank + " " + operation + " returned " + e.getStatusCode().value());
        } catch (HttpClientErrorException e) {
            if (e instanceof HttpClientErrorException.NotFound && "inquiry".equals(operation)) {
                throw e;
            }
            throw new BankClientErrorException(bank + " " + operation + " rejected with " + e.getStatusCode().value());
        } catch (ResourceAccessException e) {
            throw classify(bank, operation, e);
        }
    }

    private static BankCallException classify(BankCode bank, String operation, ResourceAccessException e) {
        Throwable cause = e.getCause();
        // HttpConnectTimeoutException, HttpTimeoutException'ın alt sınıfı; önce kontrol edilmeli
        if (cause instanceof HttpConnectTimeoutException || cause instanceof ConnectException
                || cause instanceof UnresolvedAddressException) {
            return new BankConnectException(bank + " " + operation + " connection failed: " + cause.getClass().getSimpleName(), e);
        }
        return new BankTimeoutException(bank + " " + operation + " no response: "
                + (cause == null ? e.getMessage() : cause.getClass().getSimpleName()), e);
    }
}
