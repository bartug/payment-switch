/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.routing.service.impl;

import com.bartugsevindik.paymentswitch.common.enums.BankCode;
import com.bartugsevindik.paymentswitch.common.exception.NotFoundException;
import com.bartugsevindik.paymentswitch.routing.config.RoutingProperties;
import com.bartugsevindik.paymentswitch.routing.dto.AcquirerBankInfo;
import com.bartugsevindik.paymentswitch.routing.entity.AcquirerBank;
import com.bartugsevindik.paymentswitch.routing.repository.AcquirerBankRepository;
import com.bartugsevindik.paymentswitch.routing.service.AcquirerBankService;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
public class AcquirerBankServiceImpl implements AcquirerBankService {

    private static final String ALL = "ALL";

    private final AcquirerBankRepository acquirerBankRepository;
    private final LoadingCache<String, List<AcquirerBankInfo>> cache;

    public AcquirerBankServiceImpl(AcquirerBankRepository acquirerBankRepository, RoutingProperties properties) {
        this.acquirerBankRepository = acquirerBankRepository;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(properties.getBankCacheTtl())
                .build(key -> loadAll());
    }

    /**
     * <h1>Aktif Bankaları Getirme</h1>
     * <p>Şu an işlem alabilen bankaları döndürür. Kısa süreli cache'lenir.</p>
     *
     * @return Aktif bankalar
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-4
     */
    @Override
    public List<AcquirerBankInfo> getActiveBanks() {
        return cache.get(ALL).stream().filter(AcquirerBankInfo::active).toList();
    }

    /**
     * <h1>Tüm Bankaları Getirme</h1>
     *
     * @return Tüm bankalar
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-4
     */
    @Override
    public List<AcquirerBankInfo> getAllBanks() {
        return cache.get(ALL);
    }

    /**
     * <h1>Banka Durumu Güncelleme</h1>
     * <p>Pasife alınan bankaya yeni işlem gönderilmez. Bu pod'da hemen, diğer pod'larda cache süresi kadar sonra geçerli olur.</p>
     *
     * @param bankCode Banka kodu
     * @param active   Yeni durum
     * @return Güncel banka bilgisi
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-4
     */
    @Override
    @Transactional
    public AcquirerBankInfo updateBankStatus(@NotNull BankCode bankCode, boolean active) {
        AcquirerBank bank = acquirerBankRepository.findByBankCode(bankCode)
                .orElseThrow(() -> new NotFoundException("Banka", "bankCode", bankCode));
        bank.setActive(active);
        // Commit'ten önce silinirse başka bir thread eski değeri tekrar cache'e yükleyebilir
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                cache.invalidateAll();
            }
        });
        log.warn("Acquirer bank status changed. bankCode={}, active={}", bankCode, active);
        return toInfo(bank);
    }

    private List<AcquirerBankInfo> loadAll() {
        return acquirerBankRepository.findAll().stream()
                .map(AcquirerBankServiceImpl::toInfo)
                .sorted(Comparator.comparing(AcquirerBankInfo::bankCode))
                .toList();
    }

    private static AcquirerBankInfo toInfo(AcquirerBank bank) {
        return new AcquirerBankInfo(bank.getBankCode(), Boolean.TRUE.equals(bank.getActive()),
                bank.getOnUsRateBps(), bank.getOffUsRateBps());
    }
}
