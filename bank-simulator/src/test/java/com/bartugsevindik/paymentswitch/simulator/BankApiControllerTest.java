/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.simulator;

import com.bartugsevindik.paymentswitch.common.enums.BankCode;
import com.bartugsevindik.paymentswitch.simulator.dto.ChaosSettings;
import com.bartugsevindik.paymentswitch.simulator.service.BankSimulatorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BankApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BankSimulatorService bankSimulatorService;

    @AfterEach
    void resetChaos() {
        for (BankCode bank : BankCode.values()) {
            bankSimulatorService.updateChaos(bank, ChaosSettings.none());
        }
    }

    @Test
    void onaylananIslemSorgulanabilir() throws Exception {
        String orderId = UUID.randomUUID().toString();
        authorize(orderId, 125050)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.responseCode").value("00"))
                .andExpect(jsonPath("$.authCode").isNotEmpty());

        mockMvc.perform(get("/banks/YKB/v1/transactions/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void tutarKurusu51iseYetersizBakiye() throws Exception {
        authorize(UUID.randomUUID().toString(), 100051)
                .andExpect(jsonPath("$.status").value("DECLINED"))
                .andExpect(jsonPath("$.responseCode").value("51"));
    }

    @Test
    void ayniOrderIdIkinciKezYeniIslemOlusturmaz() throws Exception {
        String orderId = UUID.randomUUID().toString();
        String first = authorize(orderId, 125050).andReturn().getResponse().getContentAsString();
        String second = authorize(orderId, 125050).andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(second).isEqualTo(first);
    }

    @Test
    void bankayaUlasmayanIslemSorgudaBulunmaz() throws Exception {
        mockMvc.perform(get("/banks/YKB/v1/transactions/{orderId}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void reversalSonrasiGelenAsilIstekIptalOlarakKalir() throws Exception {
        String orderId = UUID.randomUUID().toString();
        mockMvc.perform(post("/banks/YKB/v1/transactions/{orderId}/reversal", orderId))
                .andExpect(jsonPath("$.status").value("REVERSED"));

        // Asıl istek bankaya reversal'dan sonra ulaştı; para çekilmemeli
        authorize(orderId, 125050).andExpect(jsonPath("$.status").value("REVERSED"));
    }

    @Test
    void kapaliBanka503Doner() throws Exception {
        bankSimulatorService.updateChaos(BankCode.YKB, new ChaosSettings(true, 0, 0, 0, 0));

        authorize(UUID.randomUUID().toString(), 125050)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.responseCode").value("91"));
        mockMvc.perform(get("/banks/YKB/v1/echo")).andExpect(status().isServiceUnavailable());
        mockMvc.perform(get("/banks/QNB/v1/echo")).andExpect(status().isOk());
    }

    @Test
    void kismiIadeYapilirToplamSatisiAsamaz() throws Exception {
        String orderId = UUID.randomUUID().toString();
        authorize(orderId, 100000);

        refund(orderId, "R1", 60000).andExpect(jsonPath("$.status").value("REFUNDED"));
        refund(orderId, "R2", 50000)
                .andExpect(jsonPath("$.status").value("DECLINED"))
                .andExpect(jsonPath("$.responseCode").value("13"));
        refund(orderId, "R3", 40000).andExpect(jsonPath("$.status").value("REFUNDED"));
    }

    @Test
    void ayniRefundIdIkinciKezIadeOlusturmaz() throws Exception {
        String orderId = UUID.randomUUID().toString();
        authorize(orderId, 100000);

        refund(orderId, "R1", 100000).andExpect(jsonPath("$.status").value("REFUNDED"));
        // Adapter timeout sonrası aynı refundId ile tekrar denedi; toplam aşılmış sayılmamalı
        refund(orderId, "R1", 100000)
                .andExpect(jsonPath("$.status").value("REFUNDED"))
                .andExpect(jsonPath("$.message").value("İade daha önce yapıldı"));
    }

    @Test
    void iadeYapilmisIslemIptalEdilemez() throws Exception {
        String orderId = UUID.randomUUID().toString();
        authorize(orderId, 100000);
        refund(orderId, "R1", 10000);

        mockMvc.perform(post("/banks/YKB/v1/transactions/{orderId}/void", orderId))
                .andExpect(jsonPath("$.status").value("DECLINED"))
                .andExpect(jsonPath("$.responseCode").value("12"));
    }

    @Test
    void onayliIslemIptalEdilirTekrarAyniSonucuDoner() throws Exception {
        String orderId = UUID.randomUUID().toString();
        authorize(orderId, 100000);

        mockMvc.perform(post("/banks/YKB/v1/transactions/{orderId}/void", orderId))
                .andExpect(jsonPath("$.status").value("VOIDED"));
        mockMvc.perform(post("/banks/YKB/v1/transactions/{orderId}/void", orderId))
                .andExpect(jsonPath("$.status").value("VOIDED"));
    }

    @Test
    void gunSonuDosyasindaOnayliSatislarVeIadelerVarIptallerYok() throws Exception {
        String sale = UUID.randomUUID().toString();
        String refunded = UUID.randomUUID().toString();
        String voided = UUID.randomUUID().toString();
        authorize(sale, 100000);
        authorize(refunded, 50000);
        authorize(voided, 25000);
        refund(refunded, "RF-" + refunded, 20000);
        mockMvc.perform(post("/banks/YKB/v1/transactions/{orderId}/void", voided));

        String today = java.time.LocalDate.now(java.time.ZoneId.of("Europe/Istanbul")).toString();
        String csv = mockMvc.perform(get("/banks/YKB/v1/settlement-files/{date}", today))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(csv)
                .startsWith("order_id,type,amount,currency,rrn,auth_code,operation_id,transaction_time")
                .contains(sale + ",SALE,100000,TRY")
                .contains(refunded + ",SALE,50000,TRY")
                .contains(refunded + ",REFUND,20000,TRY")
                .doesNotContain(voided);
    }

    private org.springframework.test.web.servlet.ResultActions refund(String orderId, String refundId, long amount) throws Exception {
        return mockMvc.perform(post("/banks/YKB/v1/transactions/{orderId}/refunds", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"operationId\": \"%s\", \"amount\": %d}".formatted(refundId, amount)));
    }

    private org.springframework.test.web.servlet.ResultActions authorize(String orderId, long amount) throws Exception {
        return mockMvc.perform(post("/banks/YKB/v1/authorize")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"orderId": "%s", "pan": "5400617020092306", "expiryMonth": "12", "expiryYear": "28",
                         "cvv": "000", "amount": %d, "currency": "TRY", "installmentCount": 1}
                        """.formatted(orderId, amount)));
    }
}
