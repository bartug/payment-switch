/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.routing.service.impl;

import com.bartugsevindik.paymentswitch.routing.config.RoutingProperties;
import com.bartugsevindik.paymentswitch.routing.dto.BinInfo;
import com.bartugsevindik.paymentswitch.routing.entity.BinRange;
import com.bartugsevindik.paymentswitch.routing.repository.BinRangeRepository;
import com.bartugsevindik.paymentswitch.routing.service.BinLookupService;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

@Service
public class BinLookupServiceImpl implements BinLookupService {

    private final BinRangeRepository binRangeRepository;
    private final LoadingCache<String, Optional<BinInfo>> cache;

    public BinLookupServiceImpl(BinRangeRepository binRangeRepository, RoutingProperties properties, MeterRegistry meterRegistry) {
        this.binRangeRepository = binRangeRepository;
        // Tanımsız BIN'ler de (Optional.empty) cache'lenir; aynı bilinmeyen kartla gelen her işlem DB'ye gitmez
        this.cache = Caffeine.newBuilder()
                .maximumSize(properties.getBinCacheSize())
                .expireAfterWrite(properties.getBinCacheTtl())
                .recordStats()
                .build(this::load);
        CaffeineCacheMetrics.monitor(meterRegistry, cache, "bin_lookup");
    }

    /**
     * <h1>BIN Çözümleme</h1>
     * <p>En uzun prefix eşleşmesi ile BIN bilgisini döndürür: önce 8 haneye, bulunamazsa 6 haneye bakılır.
     * Sonuçlar (bulunamayanlar dahil) cache'lenir.</p>
     *
     * @param cardBin Kartın ilk 6 ya da 8 hanesi
     * @return BIN bilgisi. Tanımsızsa boş.
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-4
     */
    @Override
    public Optional<BinInfo> lookup(@NotNull String cardBin) {
        return cache.get(cardBin);
    }

    private Optional<BinInfo> load(String cardBin) {
        Set<String> prefixes = new LinkedHashSet<>();
        if (cardBin.length() >= 8) {
            prefixes.add(cardBin.substring(0, 8));
        }
        prefixes.add(cardBin.substring(0, 6));

        return binRangeRepository.findByBinPrefixIn(prefixes).stream()
                .max(Comparator.comparingInt(range -> range.getBinPrefix().length()))
                .map(BinLookupServiceImpl::toInfo);
    }

    private static BinInfo toInfo(BinRange range) {
        return new BinInfo(range.getBinPrefix(), range.getIssuerBank(), range.getCardProgram(),
                range.getCardScheme(), range.getCardType(), Boolean.TRUE.equals(range.getCommercial()));
    }
}
