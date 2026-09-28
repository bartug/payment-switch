/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.terminal.security;

import com.bartugsevindik.paymentswitch.common.dto.ResponseMessage;
import com.bartugsevindik.paymentswitch.common.helpers.ResponseHelper;
import com.bartugsevindik.paymentswitch.payment.terminal.config.TerminalAuthProperties;
import com.bartugsevindik.paymentswitch.payment.terminal.entity.Terminal;
import com.bartugsevindik.paymentswitch.payment.terminal.enums.TerminalStatus;
import com.bartugsevindik.paymentswitch.payment.terminal.repository.TerminalRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * <h1>TerminalAuthenticationFilter</h1>
 * <p>{@code /v1/payments} altındaki her isteğin terminal imzasını doğrular. Algoritma {@link RequestSigner} içinde.</p>
 * <p>Replay koruması iki katmanlıdır: zaman damgası penceresi dışındaki istekler burada reddedilir,
 * pencere içindeki tekrarlar Idempotency-Key ile yakalanır. Key de imzaya dahil olduğu için değiştirilemez.</p>
 * <p>Terminal bulunamadı ile imza hatalı durumlarında aynı cevap döner; terminal numarası taraması yapılamaz.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-2
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TerminalAuthenticationFilter extends OncePerRequestFilter {

    public static final String TERMINAL_ID_HEADER = "X-Terminal-Id";
    public static final String TIMESTAMP_HEADER = "X-Timestamp";
    public static final String SIGNATURE_HEADER = "X-Signature";
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final String PROTECTED_PATH = "/v1/payments";
    private static final String INVALID_SIGNATURE = "İstek imzası doğrulanamadı.";
    private static final List<String> SINGLE_VALUE_HEADERS =
            List.of(TERMINAL_ID_HEADER, TIMESTAMP_HEADER, SIGNATURE_HEADER, IDEMPOTENCY_KEY_HEADER);

    private final TerminalRepository terminalRepository;
    private final TerminalSecretCipher secretCipher;
    private final TerminalAuthProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock = Clock.systemUTC();

    @Override
    protected boolean shouldNotFilter(@NotNull HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !(path.equals(PROTECTED_PATH) || path.startsWith(PROTECTED_PATH + "/"));
    }

    @Override
    protected void doFilterInternal(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response,
                                    @NotNull FilterChain filterChain) throws ServletException, IOException {
        // Aynı header iki kez gelirse filter ilkini, controller birleşik halini okur; imzalanan ile işlenen key farklılaşır
        Optional<String> duplicated = SINGLE_VALUE_HEADERS.stream()
                .filter(name -> Collections.list(request.getHeaders(name)).size() > 1)
                .findFirst();
        if (duplicated.isPresent()) {
            reject(response, HttpServletResponse.SC_BAD_REQUEST,
                    ResponseHelper.badRequest(duplicated.get() + " header'ı birden fazla gönderilemez."));
            return;
        }

        String terminalId = request.getHeader(TERMINAL_ID_HEADER);
        String timestamp = request.getHeader(TIMESTAMP_HEADER);
        String signature = request.getHeader(SIGNATURE_HEADER);
        if (terminalId == null || timestamp == null || signature == null) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED,
                    ResponseHelper.unauthorized(TERMINAL_ID_HEADER + ", " + TIMESTAMP_HEADER + " ve " + SIGNATURE_HEADER + " header'ları zorunludur."));
            return;
        }

        if (!isWithinTolerance(timestamp)) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED,
                    ResponseHelper.unauthorized("İstek zaman damgası geçersiz ya da süresi dolmuş."));
            return;
        }

        byte[] body = request.getInputStream().readNBytes(properties.getMaxBodyBytes() + 1);
        if (body.length > properties.getMaxBodyBytes()) {
            reject(response, HttpServletResponse.SC_BAD_REQUEST, ResponseHelper.badRequest("İstek gövdesi çok büyük."));
            return;
        }

        Optional<Terminal> terminal = terminalRepository.findByTerminalId(terminalId);
        if (terminal.isEmpty() || !isSignatureValid(terminal.get(), request, timestamp, body, signature)) {
            log.warn("Terminal signature rejected. terminalId={}, path={}", terminalId, request.getRequestURI());
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, ResponseHelper.unauthorized(INVALID_SIGNATURE));
            return;
        }

        // İmza kontrolünden sonra; aksi halde pasif terminal numaraları dışarıdan tespit edilebilir
        if (terminal.get().getTerminalStatus() != TerminalStatus.ACTIVE) {
            reject(response, HttpServletResponse.SC_FORBIDDEN, ResponseHelper.forbidden("Terminal işlem almaya kapalı."));
            return;
        }

        Terminal authenticated = terminal.get();
        request.setAttribute(TerminalPrincipal.REQUEST_ATTRIBUTE, new TerminalPrincipal(
                authenticated.getTerminalId(), authenticated.getMerchantId(), authenticated.getTerminalType()));
        filterChain.doFilter(new CachedBodyHttpServletRequest(request, body), response);
    }

    private boolean isWithinTolerance(String timestamp) {
        try {
            Instant requestTime = Instant.ofEpochSecond(Long.parseLong(timestamp));
            Duration drift = Duration.between(requestTime, clock.instant()).abs();
            return drift.compareTo(properties.getTimestampTolerance()) <= 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isSignatureValid(Terminal terminal, HttpServletRequest request, String timestamp,
                                     byte[] body, String signature) {
        String path = request.getRequestURI() + (request.getQueryString() != null ? "?" + request.getQueryString() : "");
        String stringToSign = RequestSigner.stringToSign(request.getMethod(), path, timestamp,
                request.getHeader(IDEMPOTENCY_KEY_HEADER), body);
        String expected = RequestSigner.sign(secretCipher.decrypt(terminal.getSecretCiphertext()), stringToSign);
        return RequestSigner.matches(expected, signature);
    }

    private void reject(HttpServletResponse response, int status, ResponseMessage message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), message);
    }
}
