/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.vault.security;

import com.bartugsevindik.paymentswitch.common.helpers.ResponseHelper;
import com.bartugsevindik.paymentswitch.payment.vault.config.CardVaultProperties;
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
import java.security.MessageDigest;

/**
 * <h1>InternalApiTokenFilter</h1>
 * <p>{@code /internal/**} uç noktalarına sadece servislerin erişmesini sağlar. Bu uç noktalar kart verisi döndürdüğü
 * için dışarıya (ingress) hiç açılmamalıdır; token ikinci bir savunma katmanıdır.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-5
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InternalApiTokenFilter extends OncePerRequestFilter {

    public static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private final CardVaultProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(@NotNull HttpServletRequest request) {
        return !request.getRequestURI().substring(request.getContextPath().length()).startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response,
                                    @NotNull FilterChain filterChain) throws ServletException, IOException {
        String token = request.getHeader(INTERNAL_TOKEN_HEADER);
        boolean valid = token != null && MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8), properties.getInternalApiToken().getBytes(StandardCharsets.UTF_8));
        if (!valid) {
            log.warn("Internal API access rejected. path={}, remote={}", request.getRequestURI(), request.getRemoteAddr());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            objectMapper.writeValue(response.getOutputStream(), ResponseHelper.unauthorized("Yetkisiz erişim."));
            return;
        }
        filterChain.doFilter(request, response);
    }
}
