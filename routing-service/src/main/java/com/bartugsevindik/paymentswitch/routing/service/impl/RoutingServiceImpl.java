/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.routing.service.impl;

import com.bartugsevindik.paymentswitch.common.event.BankAuthorizationRequestedEvent;
import com.bartugsevindik.paymentswitch.common.event.PaymentRequestedEvent;
import com.bartugsevindik.paymentswitch.common.event.PaymentRoutingResultEvent;
import com.bartugsevindik.paymentswitch.messaging.consumer.IncomingEvent;
import com.bartugsevindik.paymentswitch.messaging.inbox.InboxService;
import com.bartugsevindik.paymentswitch.messaging.outbox.OutboxService;
import com.bartugsevindik.paymentswitch.routing.dto.AcquirerBankInfo;
import com.bartugsevindik.paymentswitch.routing.dto.BinInfo;
import com.bartugsevindik.paymentswitch.routing.dto.RoutingResultDTO;
import com.bartugsevindik.paymentswitch.routing.dto.RoutingSimulationRequest;
import com.bartugsevindik.paymentswitch.routing.entity.RoutingDecision;
import com.bartugsevindik.paymentswitch.routing.repository.RoutingDecisionRepository;
import com.bartugsevindik.paymentswitch.routing.rule.RoutingContext;
import com.bartugsevindik.paymentswitch.routing.rule.RoutingEngine;
import com.bartugsevindik.paymentswitch.routing.rule.RoutingResult;
import com.bartugsevindik.paymentswitch.routing.service.AcquirerBankService;
import com.bartugsevindik.paymentswitch.routing.service.BinLookupService;
import com.bartugsevindik.paymentswitch.routing.service.RoutingService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoutingServiceImpl implements RoutingService {

    private static final String CONSUMER = "routing-service";
    private static final String AGGREGATE_TYPE = "PAYMENT";

    private final BinLookupService binLookupService;
    private final AcquirerBankService acquirerBankService;
    private final RoutingEngine routingEngine;
    private final RoutingDecisionRepository routingDecisionRepository;
    private final InboxService inboxService;
    private final OutboxService outboxService;
    private final MeterRegistry meterRegistry;

    /**
     * <h1>Ödeme Yönlendirme</h1>
     * <p>Kararı verir ve tek transaction içinde kaydeder: inbox kaydı, routing kararı, banka topic'ine giden istek
     * ve payment-api'ye giden sonuç. Aynı event ikinci kez gelirse hiçbir şey yapılmaz.</p>
     *
     * @param event {@code payment.requested} topic'inden gelen event
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-4
     */
    @Override
    @Transactional
    public void route(@NotNull IncomingEvent<PaymentRequestedEvent> event) {
        PaymentRequestedEvent payment = event.payload();
        if (!inboxService.markProcessed(event.eventId(), CONSUMER)) {
            return;
        }
        // Farklı event-id ile aynı ödeme gelirse (örn. outbox'tan elle yeniden gönderim) ikinci karar verilmez
        if (routingDecisionRepository.existsByPaymentId(payment.paymentId())) {
            log.warn("Payment already routed, skipping. paymentId={}, eventId={}", payment.paymentId(), event.eventId());
            return;
        }

        Optional<BinInfo> binInfo = binLookupService.lookup(payment.cardBin());
        RoutingResult result = routingEngine.decide(
                new RoutingContext(payment.installmentCount(), binInfo, acquirerBankService.getActiveBanks()));

        routingDecisionRepository.save(RoutingDecision.builder()
                .paymentId(payment.paymentId())
                .outcome(result.outcome())
                .bankCode(result.bankCode())
                .onUs(result.onUs())
                .reason(result.reason())
                .cardBin(payment.cardBin())
                .installmentCount(payment.installmentCount())
                .build());

        if (result.isRouted()) {
            outboxService.enqueue(AGGREGATE_TYPE, payment.paymentId(), result.bankCode().requestTopic(),
                    toBankRequest(payment, result));
        }
        outboxService.enqueue(AGGREGATE_TYPE, payment.paymentId(), PaymentRoutingResultEvent.TOPIC, toResultEvent(payment, result));

        meterRegistry.counter("routing.decisions", "outcome", result.outcome().name(), "reason", result.reason().name(),
                "bank", result.bankCode() == null ? "NONE" : result.bankCode().name()).increment();
        log.info("Payment routed. paymentId={}, outcome={}, bank={}, onUs={}, reason={}, bin={}, installment={}",
                payment.paymentId(), result.outcome(), result.bankCode(), result.onUs(), result.reason(),
                payment.cardBin(), payment.installmentCount());
    }

    /**
     * <h1>Routing Simülasyonu</h1>
     * <p>Kararı kaydetmeden ve event üretmeden döndürür. Kuralları ve banka durumlarını denemek için.</p>
     *
     * @param request BIN ve taksit sayısı
     * @return Karar ve karara giden bilgiler
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-4
     */
    @Override
    public RoutingResultDTO simulate(@NotNull RoutingSimulationRequest request) {
        Optional<BinInfo> binInfo = binLookupService.lookup(request.getCardBin());
        List<AcquirerBankInfo> activeBanks = acquirerBankService.getActiveBanks();
        RoutingResult result = routingEngine.decide(new RoutingContext(request.getInstallmentCount(), binInfo, activeBanks));

        return RoutingResultDTO.builder()
                .outcome(result.outcome())
                .bankCode(result.bankCode())
                .onUs(result.onUs())
                .reason(result.reason())
                .description(result.reason().getDescription())
                .binInfo(binInfo.orElse(null))
                .activeBanks(activeBanks)
                .build();
    }

    private static BankAuthorizationRequestedEvent toBankRequest(PaymentRequestedEvent payment, RoutingResult result) {
        return new BankAuthorizationRequestedEvent(
                payment.paymentId(),
                payment.merchantId(),
                payment.terminalId(),
                payment.terminalType(),
                result.bankCode(),
                result.onUs(),
                payment.cardBin(),
                payment.cardLast4(),
                payment.cardToken(),
                payment.amount(),
                payment.currency(),
                payment.installmentCount(),
                result.reason().name(),
                Instant.now());
    }

    private static PaymentRoutingResultEvent toResultEvent(PaymentRequestedEvent payment, RoutingResult result) {
        return new PaymentRoutingResultEvent(
                payment.paymentId(),
                result.isRouted(),
                result.bankCode(),
                result.onUs(),
                result.reason().name(),
                result.reason().getDescription(),
                Instant.now());
    }
}
