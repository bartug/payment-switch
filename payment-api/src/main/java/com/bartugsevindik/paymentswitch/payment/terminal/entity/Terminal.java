/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.terminal.entity;

import com.bartugsevindik.paymentswitch.common.entity.BaseEntity;
import com.bartugsevindik.paymentswitch.common.enums.TerminalType;
import com.bartugsevindik.paymentswitch.payment.terminal.enums.TerminalStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * <h1>Terminal</h1>
 * <p>Ödeme gönderebilen fiziki veya sanal POS. Her terminalin istekleri imzaladığı kendine ait bir secret'ı vardır.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-2
 */
@Entity
@Table(name = "terminal")
@Data
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = false)
public class Terminal extends BaseEntity {

    @Column(name = "terminal_id", nullable = false, unique = true, length = 32, updatable = false)
    private String terminalId;

    @Column(name = "merchant_id", nullable = false, length = 32)
    private String merchantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "terminal_type", nullable = false, length = 16)
    private TerminalType terminalType;

    /**
     * AES-GCM ile şifrelenmiş secret. HMAC doğrulaması için secret'ın kendisi gerektiğinden hash'lenemez,
     * şifrelenerek saklanır.
     */
    @ToString.Exclude
    @Column(name = "secret_ciphertext", nullable = false)
    private String secretCiphertext;

    @Enumerated(EnumType.STRING)
    @Column(name = "terminal_status", nullable = false, length = 16)
    private TerminalStatus terminalStatus;
}
