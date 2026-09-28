/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.vault.entity;

import com.bartugsevindik.paymentswitch.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * <h1>CardVaultEntry</h1>
 * <p>Bankaya gönderilene kadar kart verisinin şifreli tutulduğu kayıt. Kafka'ya kart verisi yazılmaz, sadece
 * {@code token} gider; Kafka log'u PCI kapsamı dışında kalır.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-5
 */
@Entity
@Table(name = "card_vault")
@Data
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = false)
public class CardVaultEntry extends BaseEntity {

    /**
     * Rastgele UUID. Kart numarasından türetilmez; token'dan karta geri gidilemez.
     */
    @Column(name = "token", nullable = false, unique = true, length = 36, updatable = false)
    private String token;

    @Column(name = "payment_id", nullable = false, unique = true, length = 36, updatable = false)
    private String paymentId;

    /**
     * Kart numarası, son kullanma tarihi ve CVV; AES-256-GCM ile şifreli JSON.
     */
    @ToString.Exclude
    @Column(name = "card_data_ciphertext", nullable = false)
    private String cardDataCiphertext;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
}
