/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.bank.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Bankanın cevap formatı. Bankaların eklediği bilinmeyen alanlar entegrasyonu bozmasın diye yok sayılır.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BankApiResponse(String orderId, String status, String responseCode, String authCode, String rrn, String message) {
}
