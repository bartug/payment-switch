/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.terminal.repository;

import com.bartugsevindik.paymentswitch.payment.terminal.entity.Terminal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TerminalRepository extends JpaRepository<Terminal, Long> {

    /**
     * Terminal numarası ile kaydı getirir.
     *
     * @param terminalId Terminal numarası
     * @return Terminal
     */
    Optional<Terminal> findByTerminalId(String terminalId);

    boolean existsByTerminalId(String terminalId);
}
