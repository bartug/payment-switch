/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.reconciliation.client;

import com.bartugsevindik.paymentswitch.common.enums.BankCode;
import com.bartugsevindik.paymentswitch.payment.reconciliation.dto.SettlementRecord;
import com.bartugsevindik.paymentswitch.payment.reconciliation.enums.SettlementRecordType;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * <h1>SettlementFileClient</h1>
 * <p>Bankanın gün sonu dosyasını alır ve satırlara çevirir. Format:
 * {@code order_id,type,amount,currency,rrn,auth_code,operation_id,transaction_time}</p>
 * <p>Gerçekte her bankanın dosya formatı farklıdır (sabit uzunluklu, XML, CSV); banka bazında ayrı parser gerekir.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 29.09.2026 - PS-7
 */
@Slf4j
@Component
public class SettlementFileClient {

    private final RestClient restClient;

    public SettlementFileClient(@Qualifier("settlementFileRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * @return Dosyadaki satırlar. Dosya henüz yoksa boş liste.
     */
    public List<SettlementRecord> fetch(@NotNull BankCode bank, @NotNull LocalDate businessDate) {
        String csv;
        try {
            csv = restClient.get()
                    .uri("/banks/{bank}/v1/settlement-files/{date}", bank, businessDate)
                    .retrieve()
                    .body(String.class);
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("Settlement file not found. bank={}, date={}", bank, businessDate);
            return List.of();
        }
        return parse(csv, businessDate);
    }

    private static List<SettlementRecord> parse(String csv, LocalDate fileDate) {
        List<SettlementRecord> records = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return records;
        }
        String[] lines = csv.split("\\R");
        // İlk satır başlık
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].isBlank()) {
                continue;
            }
            String[] f = lines[i].split(",", -1);
            records.add(new SettlementRecord(f[0], SettlementRecordType.valueOf(f[1]), Long.parseLong(f[2]), f[3],
                    blankToNull(f[4]), blankToNull(f[5]), blankToNull(f[6]), fileDate));
        }
        return records;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
