/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.webhook.job;

import com.bartugsevindik.paymentswitch.payment.merchant.dto.WebhookTarget;
import com.bartugsevindik.paymentswitch.payment.merchant.service.MerchantService;
import com.bartugsevindik.paymentswitch.payment.webhook.entity.WebhookDelivery;
import com.bartugsevindik.paymentswitch.payment.webhook.service.WebhookService;
import com.bartugsevindik.paymentswitch.payment.webhook.service.WebhookSigner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * <h1>WebhookDispatcher</h1>
 * <p>Zamanı gelen bildirimleri üye işyerlerine gönderir. HTTP çağrısı transaction dışındadır; üye işyerinin yavaş
 * sunucusu DB connection'ı tutmaz. 2xx dışındaki her cevap ve her ağ hatası başarısız deneme sayılır.</p>
 * <p>Birden fazla pod çalıştırabilir; bildirimler {@code SKIP LOCKED} ile paylaşılır.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 29.09.2026 - PS-6
 */
@Slf4j
@Component
public class WebhookDispatcher {

    private final WebhookService webhookService;
    private final MerchantService merchantService;
    private final RestClient restClient;

    public WebhookDispatcher(WebhookService webhookService, MerchantService merchantService,
                             @Qualifier("webhookRestClient") RestClient restClient) {
        this.webhookService = webhookService;
        this.merchantService = merchantService;
        this.restClient = restClient;
    }

    @Scheduled(fixedDelayString = "${application.webhook.poll-interval:1s}")
    public void dispatchDue() {
        List<WebhookDelivery> due = webhookService.claimDue();
        for (WebhookDelivery delivery : due) {
            try {
                send(delivery);
            } catch (RuntimeException e) {
                log.error("Webhook dispatch failed unexpectedly. deliveryId={}", delivery.getDeliveryId(), e);
            }
        }
    }

    private void send(WebhookDelivery delivery) {
        Optional<WebhookTarget> target = merchantService.findWebhookTarget(delivery.getMerchantId());
        if (target.isEmpty()) {
            webhookService.recordFailure(delivery.getId(), null, "Üye işyerinin webhook adresi tanımlı değil.");
            return;
        }

        long timestamp = Instant.now().getEpochSecond();
        try {
            ResponseEntity<Void> response = restClient.post()
                    .uri(target.get().url())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(WebhookSigner.DELIVERY_ID_HEADER, delivery.getDeliveryId())
                    .header(WebhookSigner.EVENT_TYPE_HEADER, delivery.getEventType())
                    .header(WebhookSigner.SIGNATURE_HEADER,
                            WebhookSigner.signatureHeader(target.get().secret(), timestamp, delivery.getPayload()))
                    .body(delivery.getPayload())
                    .retrieve()
                    .toBodilessEntity();
            webhookService.recordSuccess(delivery.getId(), response.getStatusCode().value());
        } catch (RestClientResponseException e) {
            webhookService.recordFailure(delivery.getId(), e.getStatusCode().value(), "HTTP " + e.getStatusCode().value());
        } catch (RuntimeException e) {
            webhookService.recordFailure(delivery.getId(), null, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
