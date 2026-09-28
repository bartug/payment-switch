/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.vault.service.impl;

import com.bartugsevindik.paymentswitch.common.crypto.AesGcmCipher;
import com.bartugsevindik.paymentswitch.common.exception.NotFoundException;
import com.bartugsevindik.paymentswitch.payment.vault.config.CardVaultProperties;
import com.bartugsevindik.paymentswitch.payment.vault.dto.CardData;
import com.bartugsevindik.paymentswitch.payment.vault.entity.CardVaultEntry;
import com.bartugsevindik.paymentswitch.payment.vault.repository.CardVaultRepository;
import com.bartugsevindik.paymentswitch.payment.vault.service.CardVaultService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
public class CardVaultServiceImpl implements CardVaultService {

    private final CardVaultRepository cardVaultRepository;
    private final CardVaultProperties properties;
    private final ObjectMapper objectMapper;
    private final AesGcmCipher cipher;

    public CardVaultServiceImpl(CardVaultRepository cardVaultRepository, CardVaultProperties properties, ObjectMapper objectMapper) {
        this.cardVaultRepository = cardVaultRepository;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.cipher = new AesGcmCipher(properties.getEncryptionKey());
    }

    /**
     * <h1>Kart Verisi Saklama</h1>
     * <p>Kart verisini şifreleyip saklar ve yerine kullanılacak token'ı döndürür. Ödeme ile aynı transaction içinde
     * çağrılmalıdır.</p>
     *
     * @param paymentId Ödeme ID
     * @param cardData  Kart verisi
     * @return Kart token'ı
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-5
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public String store(@NotNull String paymentId, @NotNull CardData cardData) {
        String token = UUID.randomUUID().toString();
        cardVaultRepository.save(CardVaultEntry.builder()
                .token(token)
                .paymentId(paymentId)
                .cardDataCiphertext(cipher.encrypt(toJson(cardData)))
                .expiresAt(LocalDateTime.now().plus(properties.getTtl()))
                .build());
        return token;
    }

    /**
     * <h1>Kart Verisi Çözme</h1>
     * <p>Token'a ait kart verisini döndürür. Süresi dolmuş ya da silinmiş token için {@code NotFoundException} fırlatılır.</p>
     *
     * @param token Kart token'ı
     * @return Kart verisi
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-5
     */
    @Override
    @Transactional(readOnly = true)
    public CardData detokenize(@NotNull String token) {
        CardVaultEntry entry = cardVaultRepository.findByTokenAndExpiresAtAfter(token, LocalDateTime.now())
                .orElseThrow(() -> new NotFoundException("Kart verisi bulunamadı ya da süresi doldu."));
        log.info("Card data detokenized. paymentId={}", entry.getPaymentId());
        try {
            return objectMapper.readValue(cipher.decrypt(entry.getCardDataCiphertext()), CardData.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Card data could not be read. paymentId=" + entry.getPaymentId(), e);
        }
    }

    /**
     * <h1>Kart Verisi Silme</h1>
     * <p>Banka cevabı geldikten sonra kart verisine ihtiyaç kalmaz; inquiry ve reversal sipariş numarası ile yapılır.
     * PCI DSS, CVV'nin yetkilendirme sonrası (şifreli olsa bile) saklanmasını yasaklar.</p>
     *
     * @param paymentId Ödeme ID
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-5
     */
    @Override
    @Transactional
    public void purge(@NotNull String paymentId) {
        if (cardVaultRepository.deleteByPaymentId(paymentId) > 0) {
            log.info("Card data purged. paymentId={}", paymentId);
        }
    }

    /**
     * <h1>Süresi Dolan Kayıtları Silme</h1>
     *
     * @return Silinen kayıt sayısı
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-5
     */
    @Override
    @Transactional
    public int deleteExpired() {
        return cardVaultRepository.deleteExpired(LocalDateTime.now());
    }

    private String toJson(CardData cardData) {
        try {
            return objectMapper.writeValueAsString(cardData);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Card data could not be serialized", e);
        }
    }
}
