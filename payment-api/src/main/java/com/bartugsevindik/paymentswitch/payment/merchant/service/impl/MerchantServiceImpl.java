/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.merchant.service.impl;

import com.bartugsevindik.paymentswitch.common.exception.ConflictException;
import com.bartugsevindik.paymentswitch.common.exception.NotFoundException;
import com.bartugsevindik.paymentswitch.payment.merchant.dto.MerchantCreateRequest;
import com.bartugsevindik.paymentswitch.payment.merchant.dto.MerchantDTO;
import com.bartugsevindik.paymentswitch.payment.merchant.dto.WebhookTarget;
import com.bartugsevindik.paymentswitch.payment.merchant.entity.Merchant;
import com.bartugsevindik.paymentswitch.payment.merchant.repository.MerchantRepository;
import com.bartugsevindik.paymentswitch.payment.merchant.service.MerchantService;
import com.bartugsevindik.paymentswitch.payment.terminal.security.TerminalSecretCipher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantServiceImpl implements MerchantService {

    private static final String SECRET_PREFIX = "whsec_";

    private final MerchantRepository merchantRepository;
    private final TerminalSecretCipher secretCipher;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * <h1>Üye İşyeri Oluşturma</h1>
     * <p>Üye işyerini tanımlar ve webhook secret'ı üretir. Secret sadece bu cevapta açık döner.</p>
     *
     * @param request Üye işyeri bilgileri
     * @return Secret'ı içeren üye işyeri bilgisi
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 29.09.2026 - PS-6
     */
    @Override
    @Transactional
    public MerchantDTO createMerchant(@NotNull MerchantCreateRequest request) {
        if (merchantRepository.existsByMerchantId(request.getMerchantId())) {
            throw new ConflictException("Üye işyeri zaten tanımlı. MerchantId: " + request.getMerchantId());
        }
        String secret = generateSecret();
        Merchant merchant = merchantRepository.save(Merchant.builder()
                .merchantId(request.getMerchantId())
                .name(request.getName())
                .webhookUrl(request.getWebhookUrl())
                .webhookSecretCiphertext(secretCipher.encrypt(secret))
                .build());
        log.info("Merchant created. merchantId={}, webhookEnabled={}", merchant.getMerchantId(), merchant.getWebhookUrl() != null);
        return toDto(merchant, secret);
    }

    /**
     * <h1>Webhook Secret Yenileme</h1>
     * <p>Yeni secret üretir, eskisi hemen geçersiz olur. Secret sızdığında kullanılır.</p>
     *
     * @param merchantId Üye işyeri numarası
     * @return Yeni secret'ı içeren üye işyeri bilgisi
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 29.09.2026 - PS-6
     */
    @Override
    @Transactional
    public MerchantDTO rotateWebhookSecret(@NotNull String merchantId) {
        Merchant merchant = merchantRepository.findByMerchantId(merchantId)
                .orElseThrow(() -> new NotFoundException("Üye işyeri", "merchantId", merchantId));
        String secret = generateSecret();
        merchant.setWebhookSecretCiphertext(secretCipher.encrypt(secret));
        log.warn("Webhook secret rotated. merchantId={}", merchantId);
        return toDto(merchant, secret);
    }

    /**
     * <h1>Webhook Hedefini Getirme</h1>
     *
     * @param merchantId Üye işyeri numarası
     * @return Webhook adresi ve secret. Üye işyeri yoksa ya da webhook tanımlı değilse boş.
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 29.09.2026 - PS-6
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<WebhookTarget> findWebhookTarget(@NotNull String merchantId) {
        return merchantRepository.findByMerchantId(merchantId)
                .filter(merchant -> merchant.getWebhookUrl() != null && merchant.getWebhookSecretCiphertext() != null)
                .map(merchant -> new WebhookTarget(merchant.getWebhookUrl(), secretCipher.decrypt(merchant.getWebhookSecretCiphertext())));
    }

    /**
     * Ön ek, secret'ın ne olduğunu (ve sızarsa hangi sistemden olduğunu) belli eder; secret scanner'lar da tanıyabilir.
     */
    private String generateSecret() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return SECRET_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static MerchantDTO toDto(Merchant merchant, String secret) {
        return MerchantDTO.builder()
                .merchantId(merchant.getMerchantId())
                .name(merchant.getName())
                .webhookUrl(merchant.getWebhookUrl())
                .webhookSecret(secret)
                .build();
    }
}
