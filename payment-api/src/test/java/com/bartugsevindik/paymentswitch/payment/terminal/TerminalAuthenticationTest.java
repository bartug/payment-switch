/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.terminal;

import com.bartugsevindik.paymentswitch.payment.controller.PaymentController;
import com.bartugsevindik.paymentswitch.payment.terminal.entity.Terminal;
import com.bartugsevindik.paymentswitch.payment.terminal.enums.TerminalStatus;
import com.bartugsevindik.paymentswitch.payment.terminal.repository.TerminalRepository;
import com.bartugsevindik.paymentswitch.payment.terminal.security.TerminalAuthenticationFilter;
import com.bartugsevindik.paymentswitch.payment.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TerminalAuthenticationTest extends AbstractIntegrationTest {

    private static final String INVALID_SIGNATURE = "İstek imzası doğrulanamadı.";

    @Autowired
    private TerminalRepository terminalRepository;

    @Test
    void imzaHeaderlariYoksa401() throws Exception {
        mockMvc.perform(post("/v1/payments")
                        .header(PaymentController.IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("X-Signature")));
    }

    @Test
    void yanlisSecretIleImzalananIstek401() throws Exception {
        TestTerminal terminal = newTerminal();
        TestTerminal wrongSecret = new TestTerminal(terminal.terminalId(), terminal.merchantId(), "baska-bir-secret");

        mockMvc.perform(signedPost(wrongSecret, UUID.randomUUID().toString(), VALID_REQUEST))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(INVALID_SIGNATURE));
    }

    @Test
    void olmayanTerminalIleImzaHatasiAyniCevabiAlir() throws Exception {
        TestTerminal unknown = new TestTerminal("TRMYOK0000", "MRCYOK0000", "secret");

        mockMvc.perform(signedPost(unknown, UUID.randomUUID().toString(), VALID_REQUEST))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(INVALID_SIGNATURE));
    }

    @Test
    void imzalandiktanSonraTutariDegistirilenIstek401() throws Exception {
        TestTerminal terminal = newTerminal();
        String key = UUID.randomUUID().toString();
        MockHttpServletRequestBuilder tampered = signedPost(terminal, key, VALID_REQUEST)
                .content(VALID_REQUEST.replace("1250.50", "1.00"));

        mockMvc.perform(tampered)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(INVALID_SIGNATURE));
    }

    @Test
    void imzalandiktanSonraIdempotencyKeyiDegistirilenIstek401() throws Exception {
        TestTerminal terminal = newTerminal();
        long timestamp = Instant.now().getEpochSecond();
        String signature = signedPost(terminal, UUID.randomUUID().toString(), VALID_REQUEST, timestamp)
                .buildRequest(new MockServletContext())
                .getHeader(TerminalAuthenticationFilter.SIGNATURE_HEADER);

        // Yakalanan istekteki imza korunup key değiştiriliyor; yeni ödeme oluşturulmaya çalışılıyor
        mockMvc.perform(post("/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST)
                        .header(TerminalAuthenticationFilter.TERMINAL_ID_HEADER, terminal.terminalId())
                        .header(TerminalAuthenticationFilter.TIMESTAMP_HEADER, String.valueOf(timestamp))
                        .header(TerminalAuthenticationFilter.SIGNATURE_HEADER, signature)
                        .header(PaymentController.IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(INVALID_SIGNATURE));
    }

    @Test
    void ikinciIdempotencyKeyHeaderiEklenenIstek400() throws Exception {
        MockHttpServletRequestBuilder smuggled = signedPost(newTerminal(), UUID.randomUUID().toString(), VALID_REQUEST)
                .header(PaymentController.IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString());

        mockMvc.perform(smuggled)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Idempotency-Key header'ı birden fazla gönderilemez."));
    }

    @Test
    void suresiDolmusZamanDamgasi401() throws Exception {
        long tenMinutesAgo = Instant.now().minusSeconds(600).getEpochSecond();

        mockMvc.perform(signedPost(newTerminal(), UUID.randomUUID().toString(), VALID_REQUEST, tenMinutesAgo))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", containsString("zaman damgası")));
    }

    @Test
    void gelecektekiZamanDamgasi401() throws Exception {
        long tenMinutesLater = Instant.now().plusSeconds(600).getEpochSecond();

        mockMvc.perform(signedPost(newTerminal(), UUID.randomUUID().toString(), VALID_REQUEST, tenMinutesLater))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void pasifTerminal403() throws Exception {
        TestTerminal terminal = newTerminal();
        mockMvc.perform(put("/v1/admin/terminals/{terminalId}/passive", terminal.terminalId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.object.terminalStatus").value("PASSIVE"));

        mockMvc.perform(signedPost(terminal, UUID.randomUUID().toString(), VALID_REQUEST))
                .andExpect(status().isForbidden());
    }

    @Test
    void terminalBaskaUyeIsyerininOdemesiniGoremez() throws Exception {
        TestTerminal owner = newTerminal();
        TestTerminal other = newTerminal();
        String paymentId = paymentId(mockMvc.perform(signedPost(owner, UUID.randomUUID().toString(), VALID_REQUEST))
                .andExpect(status().isAccepted()).andReturn().getResponse());

        mockMvc.perform(signedGet(other, "/v1/payments/" + paymentId))
                .andExpect(status().isNotFound());
        mockMvc.perform(signedGet(owner, "/v1/payments/" + paymentId))
                .andExpect(status().isOk());
    }

    @Test
    void secretDbdeSifreliTutulur() {
        TestTerminal terminal = newTerminal();
        Terminal saved = terminalRepository.findByTerminalId(terminal.terminalId()).orElseThrow();

        assertThat(saved.getSecretCiphertext()).startsWith("v1:").doesNotContain(terminal.secret());
        assertThat(saved.getTerminalStatus()).isEqualTo(TerminalStatus.ACTIVE);
    }

    @Test
    void terminalOlusturmaSecretiBirKezDoner() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        String body = """
                {"terminalId": "TRM%s", "merchantId": "MRC%s", "terminalType": "PHYSICAL"}
                """.formatted(suffix, suffix);

        mockMvc.perform(post("/v1/admin/terminals").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.object.secret").isNotEmpty());
        mockMvc.perform(post("/v1/admin/terminals").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void imzaHeaderAdlariSabit() {
        // Terminal SDK'ları bu isimlere bağımlı; değişirse sahadaki cihazlar çalışmaz
        assertThat(TerminalAuthenticationFilter.TERMINAL_ID_HEADER).isEqualTo("X-Terminal-Id");
        assertThat(TerminalAuthenticationFilter.TIMESTAMP_HEADER).isEqualTo("X-Timestamp");
        assertThat(TerminalAuthenticationFilter.SIGNATURE_HEADER).isEqualTo("X-Signature");
    }
}
