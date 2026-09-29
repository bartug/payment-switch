/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.webhook.service;

import com.bartugsevindik.paymentswitch.payment.entity.Payment;
import com.bartugsevindik.paymentswitch.payment.webhook.dto.WebhookDeliveryDTO;
import com.bartugsevindik.paymentswitch.payment.webhook.entity.WebhookDelivery;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <h1>WebhookService</h1>
 * <p>Üye işyeri bildirimlerinin kaydı ve teslimat durumu.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 29.09.2026 - PS-6
 */
@Service
public interface WebhookService {

    /**
     * <h1>Bildirim Kaydetme</h1>
     * <p>Ödemenin yeni durumu bildirilecek bir sonuçsa ve üye işyerinin webhook adresi varsa bildirimi kaydeder.
     * Ödeme durum değişikliğiyle <b>aynı transaction</b> içinde çağrılmalıdır.</p>
     *
     * @param payment Durumu değişen ödeme
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 29.09.2026 - PS-6
     */
    void enqueue(Payment payment);

    List<WebhookDelivery> claimDue();

    void recordSuccess(Long id, int statusCode);

    void recordFailure(Long id, Integer statusCode, String error);

    /**
     * <h1>Tekrar Gönderme</h1>
     * <p>Başarısız ya da teslim edilmiş bildirimi hemen tekrar gönderilmek üzere kuyruğa alır.</p>
     *
     * @param deliveryId Bildirim ID
     * @return Güncel teslimat kaydı
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 29.09.2026 - PS-6
     */
    WebhookDeliveryDTO redeliver(String deliveryId);

    /**
     * <h1>Ödemenin Bildirimlerini Getirme</h1>
     *
     * @param paymentId Ödeme ID
     * @return Teslimat kayıtları
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 29.09.2026 - PS-6
     */
    List<WebhookDeliveryDTO> getDeliveries(String paymentId);
}
