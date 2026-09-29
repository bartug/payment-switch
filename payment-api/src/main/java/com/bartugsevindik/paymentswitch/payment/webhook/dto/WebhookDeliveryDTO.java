/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.webhook.dto;

import com.bartugsevindik.paymentswitch.payment.webhook.enums.WebhookDeliveryStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "WebhookDeliveryDTO", description = "Webhook teslimat kaydı")
public record WebhookDeliveryDTO(
        @Schema(description = "Bildirim ID") String deliveryId,
        @Schema(description = "Olay tipi", example = "payment.approved") String eventType,
        @Schema(description = "Durum", example = "DELIVERED") WebhookDeliveryStatus status,
        @Schema(description = "Deneme sayısı", example = "1") Integer attempts,
        @Schema(description = "Son HTTP cevap kodu", example = "200") Integer lastStatusCode,
        @Schema(description = "Son hata") String lastError,
        @Schema(description = "Bir sonraki deneme zamanı") LocalDateTime nextAttemptAt,
        @Schema(description = "Teslim zamanı") LocalDateTime deliveredAt
) {
}
