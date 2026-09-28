/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.bank.service.impl;

import com.bartugsevindik.paymentswitch.bank.client.BankApiClient;
import com.bartugsevindik.paymentswitch.bank.client.BankCallException;
import com.bartugsevindik.paymentswitch.bank.dto.BankApiResponse;
import com.bartugsevindik.paymentswitch.bank.dto.BankOutcome;
import com.bartugsevindik.paymentswitch.bank.entity.BankTransaction;
import com.bartugsevindik.paymentswitch.bank.enums.BankTransactionStatus;
import com.bartugsevindik.paymentswitch.bank.service.BankTransactionService;
import com.bartugsevindik.paymentswitch.bank.service.RecoveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecoveryServiceImpl implements RecoveryService {

    private final BankTransactionService bankTransactionService;
    private final BankApiClient bankApiClient;

    /**
     * <h1>Recovery Turu</h1>
     * <p>Takılı işlemleri UNKNOWN yapar, zamanı gelen inquiry ve reversal işlerini çalıştırır.</p>
     *
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-5
     */
    @Override
    public void runOnce() {
        bankTransactionService.recoverStuckSending();

        List<BankTransaction> due = bankTransactionService.claimDue();
        for (BankTransaction transaction : due) {
            // Bir işlemdeki beklenmeyen hata diğer cevapsız işlemlerin netleşmesini engellememeli
            try {
                if (transaction.getStatus() == BankTransactionStatus.UNKNOWN) {
                    inquire(transaction);
                } else if (transaction.getStatus() == BankTransactionStatus.REVERSING) {
                    reverse(transaction);
                }
            } catch (RuntimeException e) {
                log.error("Recovery step failed unexpectedly. paymentId={}, status={}",
                        transaction.getPaymentId(), transaction.getStatus(), e);
            }
        }
    }

    private void inquire(BankTransaction transaction) {
        try {
            Optional<BankApiResponse> response = bankApiClient.inquire(transaction.getBankCode(), transaction.getOrderId());
            if (response.isPresent()) {
                log.info("Inquiry resolved transaction. paymentId={}, bankStatus={}",
                        transaction.getPaymentId(), response.get().status());
                bankTransactionService.complete(transaction.getId(), BankOutcome.from(response.get()));
            } else {
                bankTransactionService.startReversal(transaction.getId(), "Banka işlemi bulamadı; geç ulaşma ihtimaline karşı iptal ediliyor.");
            }
        } catch (BankCallException e) {
            log.warn("Inquiry failed. paymentId={}, attempt={}, cause={}",
                    transaction.getPaymentId(), transaction.getAttempts() + 1, e.getMessage());
            bankTransactionService.inquiryFailed(transaction.getId(), e.getMessage());
        }
    }

    private void reverse(BankTransaction transaction) {
        try {
            BankApiResponse response = bankApiClient.reverse(transaction.getBankCode(), transaction.getOrderId());
            bankTransactionService.complete(transaction.getId(), BankOutcome.reversed(response));
        } catch (BankCallException e) {
            log.warn("Reversal failed. paymentId={}, attempt={}, cause={}",
                    transaction.getPaymentId(), transaction.getAttempts() + 1, e.getMessage());
            bankTransactionService.reversalFailed(transaction.getId(), e.getMessage());
        }
    }
}
