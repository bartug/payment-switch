/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.webhook.dto;

import com.bartugsevindik.paymentswitch.payment.dto.PaymentDTO;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * <h1>WebhookPayload</h1>
 * <p>Üye işyerine giden bildirimin gövdesi. {@code id} tekildir; aynı bildirim tekrar gelirse üye işyeri bu ID ile ayıklar.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 29.09.2026 - PS-6
 */
@Schema(name = "WebhookPayload", description = "Üye işyerine giden bildirim")
public record WebhookPayload(
        @Schema(description = "Bildirim ID, tekrarları ayıklamak için") String id,
        @Schema(description = "Olay tipi", example = "payment.approved") String type,
        @Schema(description = "Olay zamanı") Instant createdAt,
        @Schema(description = "Olay anındaki ödeme") PaymentDTO data
) {
}
