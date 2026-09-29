/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.merchant.entity;

import com.bartugsevindik.paymentswitch.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * <h1>Merchant</h1>
 * <p>Üye işyeri ve ödeme sonuçlarının bildirileceği webhook adresi.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 29.09.2026 - PS-6
 */
@Entity
@Table(name = "merchant")
@Data
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = false)
public class Merchant extends BaseEntity {

    @Column(name = "merchant_id", nullable = false, unique = true, length = 32, updatable = false)
    private String merchantId;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    /**
     * Boşsa üye işyerine bildirim gönderilmez; sonucu GET ile sorgular.
     */
    @Column(name = "webhook_url", length = 512)
    private String webhookUrl;

    @ToString.Exclude
    @Column(name = "webhook_secret_ciphertext")
    private String webhookSecretCiphertext;
}
