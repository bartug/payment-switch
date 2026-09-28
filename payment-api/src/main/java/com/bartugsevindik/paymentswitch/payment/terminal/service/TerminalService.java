/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.terminal.service;

import com.bartugsevindik.paymentswitch.payment.terminal.dto.TerminalCreateRequest;
import com.bartugsevindik.paymentswitch.payment.terminal.dto.TerminalDTO;
import com.bartugsevindik.paymentswitch.payment.terminal.enums.TerminalStatus;
import org.springframework.stereotype.Service;

/**
 * <h1>TerminalService</h1>
 * <p>Ödeme gönderebilen terminallerin tanımlanması ve durum yönetimi.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-2
 */
@Service
public interface TerminalService {

    /**
     * <h1>Terminal Oluşturma</h1>
     * <p>Terminali tanımlar ve istek imzalamada kullanılacak secret'ı üretir. Secret DB'de şifreli tutulur ve
     * sadece bu cevapta açık olarak döner.</p>
     *
     * @param request Terminal bilgileri
     * @return Secret'ı içeren terminal bilgisi
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-2
     */
    TerminalDTO createTerminal(TerminalCreateRequest request);

    /**
     * <h1>Terminal Durumu Güncelleme</h1>
     * <p>Pasife çekilen terminal, imzası doğru olsa bile işlem gönderemez.</p>
     *
     * @param terminalId Terminal numarası
     * @param status     Yeni durum
     * @return Güncel terminal bilgisi
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-2
     */
    TerminalDTO updateTerminalStatus(String terminalId, TerminalStatus status);
}
