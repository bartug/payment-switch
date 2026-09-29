/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.webhook.service.impl;

import com.bartugsevindik.paymentswitch.common.exception.NotFoundException;
import com.bartugsevindik.paymentswitch.payment.entity.Payment;
import com.bartugsevindik.paymentswitch.payment.mapper.PaymentMapper;
import com.bartugsevindik.paymentswitch.payment.merchant.repository.MerchantRepository;
import com.bartugsevindik.paymentswitch.payment.webhook.config.WebhookProperties;
import com.bartugsevindik.paymentswitch.payment.webhook.dto.WebhookDeliveryDTO;
import com.bartugsevindik.paymentswitch.payment.webhook.dto.WebhookPayload;
import com.bartugsevindik.paymentswitch.payment.webhook.entity.WebhookDelivery;
import com.bartugsevindik.paymentswitch.payment.webhook.enums.WebhookDeliveryStatus;
import com.bartugsevindik.paymentswitch.payment.webhook.enums.WebhookEventType;
import com.bartugsevindik.paymentswitch.payment.webhook.repository.WebhookDeliveryRepository;
import com.bartugsevindik.paymentswitch.payment.webhook.service.WebhookService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookServiceImpl implements WebhookService {

    private static final Duration CLAIM_LEASE = Duration.ofSeconds(30);

    private final WebhookDeliveryRepository webhookDeliveryRepository;
    private final MerchantRepository merchantRepository;
    private final PaymentMapper paymentMapper;
    private final WebhookProperties properties;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    /**
     * <h1>Bildirim Kaydetme</h1>
     * <p>Ödemenin yeni durumu bildirilecek bir sonuçsa ve üye işyerinin webhook adresi varsa bildirimi kaydeder.
     * Ödeme durum değişikliğiyle <b>aynı transaction</b> içinde çağrılmalıdır.</p>
     *
     * @param payment Durumu değişen ödeme
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 29.09.2026 - PS-6
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(@NotNull Payment payment) {
        Optional<WebhookEventType> eventType = WebhookEventType.of(payment.getPaymentStatus());
        if (eventType.isEmpty()) {
            return;
        }
        boolean webhookEnabled = merchantRepository.findByMerchantId(payment.getMerchantId())
                .map(merchant -> merchant.getWebhookUrl() != null)
                .orElse(false);
        if (!webhookEnabled) {
            return;
        }

        String deliveryId = UUID.randomUUID().toString();
        WebhookPayload payload = new WebhookPayload(deliveryId, eventType.get().getValue(), Instant.now(), paymentMapper.toDto(payment));
        webhookDeliveryRepository.save(WebhookDelivery.builder()
                .deliveryId(deliveryId)
                .merchantId(payment.getMerchantId())
                .paymentId(payment.getPaymentId())
                .eventType(eventType.get().getValue())
                .payload(toJson(payload))
                .status(WebhookDeliveryStatus.PENDING)
                .attempts(0)
                .nextAttemptAt(LocalDateTime.now())
                .build());
    }

    @Override
    @Transactional
    public List<WebhookDelivery> claimDue() {
        List<WebhookDelivery> due = webhookDeliveryRepository.lockDue(LocalDateTime.now(), properties.getBatchSize());
        // HTTP çağrısı transaction dışında yapılacak; kayıt süreli olarak başka pod'lara kapatılır
        due.forEach(delivery -> delivery.setNextAttemptAt(LocalDateTime.now().plus(CLAIM_LEASE)));
        return due;
    }

    @Override
    @Transactional
    public void recordSuccess(@NotNull Long id, int statusCode) {
        WebhookDelivery delivery = webhookDeliveryRepository.findById(id).orElseThrow();
        delivery.setStatus(WebhookDeliveryStatus.DELIVERED);
        delivery.setAttempts(delivery.getAttempts() + 1);
        delivery.setLastStatusCode(statusCode);
        delivery.setLastError(null);
        delivery.setNextAttemptAt(null);
        delivery.setDeliveredAt(LocalDateTime.now());
        meterRegistry.counter("webhook.deliveries", "result", "delivered").increment();
    }

    @Override
    @Transactional
    public void recordFailure(@NotNull Long id, Integer statusCode, String error) {
        WebhookDelivery delivery = webhookDeliveryRepository.findById(id).orElseThrow();
        int attempts = delivery.getAttempts() + 1;
        delivery.setAttempts(attempts);
        delivery.setLastStatusCode(statusCode);
        delivery.setLastError(error == null ? null : error.substring(0, Math.min(error.length(), 512)));

        List<Duration> schedule = properties.getRetrySchedule();
        if (attempts > schedule.size()) {
            delivery.setStatus(WebhookDeliveryStatus.FAILED);
            delivery.setNextAttemptAt(null);
            meterRegistry.counter("webhook.deliveries", "result", "failed").increment();
            log.error("Webhook delivery failed permanently. deliveryId={}, merchantId={}, paymentId={}, attempts={}, lastError={}",
                    delivery.getDeliveryId(), delivery.getMerchantId(), delivery.getPaymentId(), attempts, error);
            return;
        }
        delivery.setNextAttemptAt(LocalDateTime.now().plus(schedule.get(attempts - 1)));
        meterRegistry.counter("webhook.deliveries", "result", "retry").increment();
        log.warn("Webhook delivery failed, will retry. deliveryId={}, attempt={}, statusCode={}, next={}",
                delivery.getDeliveryId(), attempts, statusCode, delivery.getNextAttemptAt());
    }

    /**
     * <h1>Tekrar Gönderme</h1>
     * <p>Başarısız ya da teslim edilmiş bildirimi hemen tekrar gönderilmek üzere kuyruğa alır.</p>
     *
     * @param deliveryId Bildirim ID
     * @return Güncel teslimat kaydı
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 29.09.2026 - PS-6
     */
    @Override
    @Transactional
    public WebhookDeliveryDTO redeliver(@NotNull String deliveryId) {
        WebhookDelivery delivery = webhookDeliveryRepository.findByDeliveryId(deliveryId)
                .orElseThrow(() -> new NotFoundException("Webhook bildirimi", "deliveryId", deliveryId));
        // Deneme sayacı sıfırlanır; yeni bir retry döngüsü başlar. deliveryId aynı kalır, üye işyeri tekrarı ayıklayabilir.
        delivery.setStatus(WebhookDeliveryStatus.PENDING);
        delivery.setAttempts(0);
        delivery.setNextAttemptAt(LocalDateTime.now());
        log.info("Webhook redelivery requested. deliveryId={}", deliveryId);
        return toDto(delivery);
    }

    /**
     * <h1>Ödemenin Bildirimlerini Getirme</h1>
     *
     * @param paymentId Ödeme ID
     * @return Teslimat kayıtları
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 29.09.2026 - PS-6
     */
    @Override
    @Transactional(readOnly = true)
    public List<WebhookDeliveryDTO> getDeliveries(@NotNull String paymentId) {
        return webhookDeliveryRepository.findByPaymentIdOrderByIdAsc(paymentId).stream().map(WebhookServiceImpl::toDto).toList();
    }

    private String toJson(WebhookPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Webhook payload could not be serialized", e);
        }
    }

    private static WebhookDeliveryDTO toDto(WebhookDelivery d) {
        return new WebhookDeliveryDTO(d.getDeliveryId(), d.getEventType(), d.getStatus(), d.getAttempts(),
                d.getLastStatusCode(), d.getLastError(), d.getNextAttemptAt(), d.getDeliveredAt());
    }
}
