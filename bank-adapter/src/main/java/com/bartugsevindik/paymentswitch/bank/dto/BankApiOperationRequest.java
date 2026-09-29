/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.bank.dto;

/**
 * Bankanın iptal / iade isteği formatı.
 */
public record BankApiOperationRequest(String operationId, Long amount) {
}
