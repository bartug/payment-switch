/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.ledger.service.impl;

import com.bartugsevindik.paymentswitch.common.model.Money;
import com.bartugsevindik.paymentswitch.payment.entity.Payment;
import com.bartugsevindik.paymentswitch.payment.ledger.config.LedgerProperties;
import com.bartugsevindik.paymentswitch.payment.ledger.dto.AccountBalanceDTO;
import com.bartugsevindik.paymentswitch.payment.ledger.dto.JournalEntryDTO;
import com.bartugsevindik.paymentswitch.payment.ledger.dto.JournalLineDTO;
import com.bartugsevindik.paymentswitch.payment.ledger.dto.TrialBalanceDTO;
import com.bartugsevindik.paymentswitch.payment.ledger.entity.JournalEntry;
import com.bartugsevindik.paymentswitch.payment.ledger.entity.JournalLine;
import com.bartugsevindik.paymentswitch.payment.ledger.enums.AccountType;
import com.bartugsevindik.paymentswitch.payment.ledger.enums.EntryDirection;
import com.bartugsevindik.paymentswitch.payment.ledger.enums.LedgerEntryType;
import com.bartugsevindik.paymentswitch.payment.ledger.repository.JournalEntryRepository;
import com.bartugsevindik.paymentswitch.payment.ledger.repository.JournalLineRepository;
import com.bartugsevindik.paymentswitch.payment.ledger.service.LedgerAccounts;
import com.bartugsevindik.paymentswitch.payment.ledger.service.LedgerService;
import com.bartugsevindik.paymentswitch.payment.merchant.entity.Merchant;
import com.bartugsevindik.paymentswitch.payment.merchant.repository.MerchantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerServiceImpl implements LedgerService {

    private static final BigDecimal BPS_DIVISOR = BigDecimal.valueOf(10_000);

    private final JournalEntryRepository journalEntryRepository;
    private final JournalLineRepository journalLineRepository;
    private final MerchantRepository merchantRepository;
    private final LedgerProperties properties;

    /**
     * <h1>Satış Kaydı</h1>
     * <pre>
     * Borç   BANK_RECEIVABLE:{BANKA}    tutar
     * Alacak MERCHANT_PAYABLE:{İŞYERİ}  tutar - komisyon
     * Alacak FEE_REVENUE                komisyon
     * </pre>
     *
     * @param payment Onaylanan ödeme
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 29.09.2026 - PS-7
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void postSale(@NotNull Payment payment) {
        long amount = payment.getAmount();
        long fee = commission(payment.getMerchantId(), amount);
        JournalEntry entry = newEntry(payment, LedgerEntryType.SALE, payment.getPaymentId());
        List<JournalLine> lines = new ArrayList<>(List.of(
                line(entry, LedgerAccounts.bankReceivable(payment.getBankCode()), AccountType.ASSET, EntryDirection.DEBIT, amount),
                line(entry, LedgerAccounts.merchantPayable(payment.getMerchantId()), AccountType.LIABILITY, EntryDirection.CREDIT, amount - fee)));
        if (fee > 0) {
            lines.add(line(entry, LedgerAccounts.FEE_REVENUE, AccountType.REVENUE, EntryDirection.CREDIT, fee));
        }
        save(entry, lines);
    }

    /**
     * <h1>İade Kaydı</h1>
     * <p>Komisyon iade edilmez; iade tutarı üye işyerinin alacağından düşülür.</p>
     *
     * @param payment     Ödeme
     * @param operationId İade işlemi ID
     * @param amount      İade tutarı (kuruş)
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 29.09.2026 - PS-7
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void postRefund(@NotNull Payment payment, @NotNull String operationId, long amount) {
        JournalEntry entry = newEntry(payment, LedgerEntryType.REFUND, operationId);
        save(entry, List.of(
                line(entry, LedgerAccounts.merchantPayable(payment.getMerchantId()), AccountType.LIABILITY, EntryDirection.DEBIT, amount),
                line(entry, LedgerAccounts.bankReceivable(payment.getBankCode()), AccountType.ASSET, EntryDirection.CREDIT, amount)));
    }

    /**
     * <h1>İptal Kaydı</h1>
     * <p>Satış kaydının ters kaydıdır (storno): aynı satırlar, ters yönde. Komisyon dahil her şey geri alınır;
     * işlem takasa hiç girmemiştir.</p>
     *
     * @param payment     İptal edilen ödeme
     * @param operationId İptal işlemi ID
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 29.09.2026 - PS-7
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void postVoid(@NotNull Payment payment, @NotNull String operationId) {
        JournalEntry sale = journalEntryRepository.findByPaymentIdAndEntryType(payment.getPaymentId(), LedgerEntryType.SALE)
                .orElseThrow(() -> new IllegalStateException("Sale entry not found for void. paymentId=" + payment.getPaymentId()));
        JournalEntry entry = newEntry(payment, LedgerEntryType.VOID, operationId);
        List<JournalLine> reversed = journalLineRepository.findByEntryIdOrderByIdAsc(sale.getEntryId()).stream()
                .map(original -> line(entry, original.getAccountCode(), original.getAccountType(),
                        original.getDirection().opposite(), original.getAmount()))
                .toList();
        save(entry, reversed);
    }

    @Override
    @Transactional(readOnly = true)
    public List<JournalEntryDTO> getEntries(@NotNull String paymentId) {
        List<JournalEntry> entries = journalEntryRepository.findByPaymentIdOrderByIdAsc(paymentId);
        Map<String, List<JournalLine>> linesByEntry = journalLineRepository
                .findByEntryIdInOrderByIdAsc(entries.stream().map(JournalEntry::getEntryId).toList()).stream()
                .collect(Collectors.groupingBy(JournalLine::getEntryId));
        return entries.stream().map(entry -> new JournalEntryDTO(entry.getEntryId(), entry.getEntryType(), entry.getReferenceId(),
                entry.getCreatedDate(), linesByEntry.getOrDefault(entry.getEntryId(), List.of()).stream()
                .map(l -> new JournalLineDTO(l.getAccountCode(), l.getDirection(), decimal(l.getAmount(), l.getCurrency()), l.getCurrency()))
                .toList())).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountBalanceDTO> getBalances(String accountPrefix) {
        return journalLineRepository.sumByAccount(accountPrefix == null ? "" : accountPrefix).stream().map(row -> {
            AccountType type = (AccountType) row[1];
            String currency = (String) row[2];
            long debit = ((Number) row[3]).longValue();
            long credit = ((Number) row[4]).longValue();
            long balance = type == AccountType.ASSET ? debit - credit : credit - debit;
            return new AccountBalanceDTO((String) row[0], type, currency, decimal(debit, currency), decimal(credit, currency),
                    decimal(balance, currency));
        }).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TrialBalanceDTO getTrialBalance() {
        Object[] totals = journalLineRepository.totals().getFirst();
        long debit = ((Number) totals[0]).longValue();
        long credit = ((Number) totals[1]).longValue();
        return new TrialBalanceDTO(BigDecimal.valueOf(debit, 2), BigDecimal.valueOf(credit, 2), debit == credit);
    }

    /**
     * Komisyon kuruşa yuvarlanır (HALF_UP). Yuvarlama farkı üye işyerinin alacağına yansır; kayıt her zaman dengede kalır
     * çünkü üye işyeri payı "tutar - komisyon" olarak hesaplanır, ayrıca yuvarlanmaz.
     */
    private long commission(String merchantId, long amount) {
        int rateBps = merchantRepository.findByMerchantId(merchantId)
                .map(Merchant::getCommissionRateBps)
                .orElse(properties.getDefaultCommissionRateBps());
        return BigDecimal.valueOf(amount).multiply(BigDecimal.valueOf(rateBps))
                .divide(BPS_DIVISOR, 0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    private JournalEntry newEntry(Payment payment, LedgerEntryType type, String referenceId) {
        return JournalEntry.builder()
                .entryId(UUID.randomUUID().toString())
                .paymentId(payment.getPaymentId())
                .referenceId(referenceId)
                .entryType(type)
                .currency(payment.getCurrency())
                .build();
    }

    private static JournalLine line(JournalEntry entry, String account, AccountType type, EntryDirection direction, long amount) {
        return JournalLine.builder()
                .entryId(entry.getEntryId())
                .accountCode(account)
                .accountType(type)
                .direction(direction)
                .amount(amount)
                .currency(entry.getCurrency())
                .build();
    }

    private void save(JournalEntry entry, List<JournalLine> lines) {
        journalEntryRepository.save(entry);
        journalLineRepository.saveAll(lines);
        log.info("Journal entry posted. paymentId={}, type={}, lines={}", entry.getPaymentId(), entry.getEntryType(), lines.size());
    }

    private static BigDecimal decimal(long amount, String currency) {
        return Money.ofMinor(amount, currency).toDecimal();
    }
}
