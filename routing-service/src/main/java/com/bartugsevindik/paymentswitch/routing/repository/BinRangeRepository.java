/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.routing.repository;

import com.bartugsevindik.paymentswitch.routing.entity.BinRange;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface BinRangeRepository extends JpaRepository<BinRange, Long> {

    /**
     * Verilen prefix'lere uyan kayıtları getirir. En uzun eşleşme servis tarafında seçilir.
     *
     * @param prefixes Kartın 8 ve 6 haneli prefix'leri
     * @return Eşleşen BIN kayıtları
     */
    List<BinRange> findByBinPrefixIn(Collection<String> prefixes);
}
