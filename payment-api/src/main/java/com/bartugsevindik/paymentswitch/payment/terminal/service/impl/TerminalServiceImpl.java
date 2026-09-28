/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.terminal.service.impl;

import com.bartugsevindik.paymentswitch.common.exception.ConflictException;
import com.bartugsevindik.paymentswitch.common.exception.NotFoundException;
import com.bartugsevindik.paymentswitch.payment.terminal.dto.TerminalCreateRequest;
import com.bartugsevindik.paymentswitch.payment.terminal.dto.TerminalDTO;
import com.bartugsevindik.paymentswitch.payment.terminal.entity.Terminal;
import com.bartugsevindik.paymentswitch.payment.terminal.enums.TerminalStatus;
import com.bartugsevindik.paymentswitch.payment.terminal.repository.TerminalRepository;
import com.bartugsevindik.paymentswitch.payment.terminal.security.TerminalSecretCipher;
import com.bartugsevindik.paymentswitch.payment.terminal.service.TerminalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class TerminalServiceImpl implements TerminalService {

    private static final int SECRET_BYTES = 32;

    private final TerminalRepository terminalRepository;
    private final TerminalSecretCipher secretCipher;
    private final SecureRandom secureRandom = new SecureRandom();

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
    @Override
    @Transactional
    public TerminalDTO createTerminal(@NotNull TerminalCreateRequest request) {
        if (terminalRepository.existsByTerminalId(request.getTerminalId())) {
            throw new ConflictException("Terminal zaten tanımlı. TerminalId: " + request.getTerminalId());
        }

        String secret = generateSecret();
        Terminal terminal = terminalRepository.save(Terminal.builder()
                .terminalId(request.getTerminalId())
                .merchantId(request.getMerchantId())
                .terminalType(request.getTerminalType())
                .secretCiphertext(secretCipher.encrypt(secret))
                .terminalStatus(TerminalStatus.ACTIVE)
                .build());
        log.info("Terminal created. terminalId={}, merchantId={}", terminal.getTerminalId(), terminal.getMerchantId());

        TerminalDTO dto = toDto(terminal);
        dto.setSecret(secret);
        return dto;
    }

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
    @Override
    @Transactional
    public TerminalDTO updateTerminalStatus(@NotNull String terminalId, @NotNull TerminalStatus status) {
        Terminal terminal = terminalRepository.findByTerminalId(terminalId)
                .orElseThrow(() -> new NotFoundException("Terminal", "terminalId", terminalId));
        terminal.setTerminalStatus(status);
        log.info("Terminal status changed. terminalId={}, status={}", terminalId, status);
        return toDto(terminal);
    }

    private String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private TerminalDTO toDto(Terminal terminal) {
        return TerminalDTO.builder()
                .terminalId(terminal.getTerminalId())
                .merchantId(terminal.getMerchantId())
                .terminalType(terminal.getTerminalType())
                .terminalStatus(terminal.getTerminalStatus())
                .build();
    }
}
