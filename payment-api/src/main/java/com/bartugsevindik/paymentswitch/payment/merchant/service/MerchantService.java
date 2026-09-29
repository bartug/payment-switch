/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.merchant.service;

import com.bartugsevindik.paymentswitch.payment.merchant.dto.MerchantCreateRequest;
import com.bartugsevindik.paymentswitch.payment.merchant.dto.MerchantDTO;
import com.bartugsevindik.paymentswitch.payment.merchant.dto.WebhookTarget;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * <h1>MerchantService</h1>
 * <p>Üye işyeri tanımlama ve webhook ayarları.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 29.09.2026 - PS-6
 */
@Service
public interface MerchantService {

    /**
     * <h1>Üye İşyeri Oluşturma</h1>
     * <p>Üye işyerini tanımlar ve webhook secret'ı üretir. Secret sadece bu cevapta açık döner.</p>
     *
     * @param request Üye işyeri bilgileri
     * @return Secret'ı içeren üye işyeri bilgisi
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 29.09.2026 - PS-6
     */
    MerchantDTO createMerchant(MerchantCreateRequest request);

    /**
     * <h1>Webhook Secret Yenileme</h1>
     * <p>Yeni secret üretir, eskisi hemen geçersiz olur. Secret sızdığında kullanılır.</p>
     *
     * @param merchantId Üye işyeri numarası
     * @return Yeni secret'ı içeren üye işyeri bilgisi
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 29.09.2026 - PS-6
     */
    MerchantDTO rotateWebhookSecret(String merchantId);

    /**
     * <h1>Webhook Hedefini Getirme</h1>
     *
     * @param merchantId Üye işyeri numarası
     * @return Webhook adresi ve secret. Üye işyeri yoksa ya da webhook tanımlı değilse boş.
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 29.09.2026 - PS-6
     */
    Optional<WebhookTarget> findWebhookTarget(String merchantId);
}
