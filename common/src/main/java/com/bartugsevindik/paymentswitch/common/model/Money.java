/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.common.model;

import com.bartugsevindik.paymentswitch.common.exception.BadRequestException;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

/**
 * <h1>Money</h1>
 * <p>Tutarı para biriminin en küçük biriminde (kuruş, cent) {@code long} olarak tutar.
 * double/float ile yapılan hesaplardaki yuvarlama hatalarının önüne geçmek için tüm servisler bu tipi kullanır.</p>
 * <p>Örnek: 150,75 TL → {@code amount = 15075, currency = TRY}</p>
 *
 * @param amount   En küçük birim cinsinden tutar
 * @param currency ISO 4217 para birimi
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-1
 */
public record Money(long amount, @NotNull Currency currency) {

    public Money {
        Objects.requireNonNull(currency, "currency");
        if (amount < 0) {
            throw new BadRequestException("Tutar negatif olamaz.");
        }
    }

    /**
     * <h1>Ondalık Tutardan Money Oluşturma</h1>
     * <p>Para biriminin küsurat hanesinden fazla ondalık içeren tutarlar reddedilir (örn. 10.005 TRY).</p>
     *
     * @param value        Ondalık tutar
     * @param currencyCode ISO 4217 para birimi kodu
     * @return Money nesnesi
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-1
     */
    public static Money of(@NotNull BigDecimal value, @NotNull String currencyCode) {
        Currency currency = toCurrency(currencyCode);
        try {
            long minor = value.movePointRight(currency.getDefaultFractionDigits()).longValueExact();
            return new Money(minor, currency);
        } catch (ArithmeticException e) {
            throw new BadRequestException("Tutar " + currency.getDefaultFractionDigits() + " haneden fazla küsurat içeremez.");
        }
    }

    public static Money ofMinor(long amount, @NotNull String currencyCode) {
        return new Money(amount, toCurrency(currencyCode));
    }

    public BigDecimal toDecimal() {
        return BigDecimal.valueOf(amount, currency.getDefaultFractionDigits());
    }

    public boolean isZero() {
        return amount == 0;
    }

    private static Currency toCurrency(String currencyCode) {
        try {
            return Currency.getInstance(currencyCode);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BadRequestException("Geçersiz para birimi: " + currencyCode);
        }
    }

    @Override
    public String toString() {
        return toDecimal().toPlainString() + " " + currency.getCurrencyCode();
    }
}
