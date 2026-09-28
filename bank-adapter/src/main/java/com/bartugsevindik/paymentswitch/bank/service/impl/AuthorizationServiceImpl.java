/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.bank.service.impl;

import com.bartugsevindik.paymentswitch.bank.client.BankApiClient;
import com.bartugsevindik.paymentswitch.bank.client.BankClientErrorException;
import com.bartugsevindik.paymentswitch.bank.client.BankConnectException;
import com.bartugsevindik.paymentswitch.bank.client.CardDataUnavailableException;
import com.bartugsevindik.paymentswitch.bank.client.CardVaultClient;
import com.bartugsevindik.paymentswitch.bank.dto.BankApiAuthorizeRequest;
import com.bartugsevindik.paymentswitch.bank.dto.BankApiResponse;
import com.bartugsevindik.paymentswitch.bank.dto.BankOutcome;
import com.bartugsevindik.paymentswitch.bank.dto.CardDetails;
import com.bartugsevindik.paymentswitch.bank.entity.BankTransaction;
import com.bartugsevindik.paymentswitch.bank.service.AuthorizationService;
import com.bartugsevindik.paymentswitch.bank.service.BankTransactionService;
import com.bartugsevindik.paymentswitch.common.event.BankAuthorizationRequestedEvent;
import com.bartugsevindik.paymentswitch.messaging.consumer.IncomingEvent;
import com.bartugsevindik.paymentswitch.messaging.inbox.InboxService;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthorizationServiceImpl implements AuthorizationService {

    private final InboxService inboxService;
    private final CardVaultClient cardVaultClient;
    private final BankApiClient bankApiClient;
    private final BankTransactionService bankTransactionService;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final BulkheadRegistry bulkheadRegistry;

    /**
     * <h1>Bankaya Gönderme</h1>
     * <p>Akış: tekrar mı → kart verisini al → işlemi sahiplen (TX) → bankayı çağır (TX dışında) → sonucu yaz (TX).</p>
     * <p>Satış isteği <b>asla retry edilmez</b>. Cevap alınamazsa işlem {@code UNKNOWN} olur ve recovery job'u
     * inquiry ile netleştirir.</p>
     *
     * @param event {@code bank.requests.{BANKA}} topic'inden gelen istek
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-5
     */
    @Override
    public void authorize(@NotNull IncomingEvent<BankAuthorizationRequestedEvent> event) {
        BankAuthorizationRequestedEvent request = event.payload();
        // Ucuz ön kontrol; tekrar gelen mesaj için kart verisi hiç istenmesin
        if (inboxService.isProcessed(event.eventId(), BankTransactionServiceImpl.CONSUMER)) {
            return;
        }

        // Kart verisi sahiplenmeden önce alınır. payment-api'ye ulaşılamazsa exception fırlar, henüz hiçbir şey
        // yazılmadığı için Kafka mesajı güvenle tekrar denenir.
        CardDetails card;
        try {
            card = cardVaultClient.detokenize(request.cardToken());
        } catch (CardDataUnavailableException e) {
            log.warn("Card data unavailable, payment will fail without calling bank. paymentId={}", request.paymentId());
            bankTransactionService.recordNotSent(event.eventId(), request, "Kart verisi bulunamadı ya da süresi doldu.");
            return;
        }

        Optional<BankTransaction> claimed = bankTransactionService.claim(event.eventId(), request);
        if (claimed.isEmpty()) {
            return;
        }
        bankTransactionService.complete(claimed.get().getId(), callBank(request, card));
    }

    /**
     * Bulkhead en dışta: bulkhead dolduğu için reddedilen istek circuit breaker'da hata sayılmaz.
     */
    private BankOutcome callBank(BankAuthorizationRequestedEvent request, CardDetails card) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(request.bankCode().name());
        Bulkhead bulkhead = bulkheadRegistry.bulkhead(request.bankCode().name());
        BankApiAuthorizeRequest apiRequest = new BankApiAuthorizeRequest(request.paymentId(), card.pan(), card.expiryMonth(),
                card.expiryYear(), card.cvv(), request.amount(), request.currency(), request.installmentCount());

        try {
            Supplier<BankApiResponse> call = CircuitBreaker.decorateSupplier(circuitBreaker,
                    () -> bankApiClient.authorize(request.bankCode(), apiRequest));
            BankApiResponse response = Bulkhead.decorateSupplier(bulkhead, call).get();
            return BankOutcome.from(response);
        } catch (CallNotPermittedException e) {
            return BankOutcome.notSent("Banka şu an işlem almıyor (circuit açık).");
        } catch (BulkheadFullException e) {
            return BankOutcome.notSent("Bankaya giden eş zamanlı işlem sınırı dolu.");
        } catch (BankConnectException e) {
            return BankOutcome.notSent("Bankaya bağlanılamadı.");
        } catch (BankClientErrorException e) {
            log.error("Bank rejected request format. paymentId={}, bank={}, cause={}", request.paymentId(), request.bankCode(), e.getMessage());
            return BankOutcome.notSent("Banka isteği reddetti.");
        } catch (RuntimeException e) {
            // Timeout, 5xx ya da beklenmeyen bir hata: istek bankaya ulaşmış olabilir, retry yok
            log.warn("Bank response unknown, will inquire. paymentId={}, bank={}, cause={}",
                    request.paymentId(), request.bankCode(), e.getMessage());
            return BankOutcome.unknown("Bankadan cevap alınamadı, işlem sorgulanıyor.");
        }
    }
}
