/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.reconciliation.service.impl;

import com.bartugsevindik.paymentswitch.common.enums.BankCode;
import com.bartugsevindik.paymentswitch.common.exception.NotFoundException;
import com.bartugsevindik.paymentswitch.common.model.Money;
import com.bartugsevindik.paymentswitch.payment.entity.Payment;
import com.bartugsevindik.paymentswitch.payment.enums.PaymentStatus;
import com.bartugsevindik.paymentswitch.payment.operation.config.PaymentOperationProperties;
import com.bartugsevindik.paymentswitch.payment.operation.entity.PaymentOperation;
import com.bartugsevindik.paymentswitch.payment.operation.enums.PaymentOperationStatus;
import com.bartugsevindik.paymentswitch.payment.operation.repository.PaymentOperationRepository;
import com.bartugsevindik.paymentswitch.payment.reconciliation.client.SettlementFileClient;
import com.bartugsevindik.paymentswitch.payment.reconciliation.config.ReconciliationProperties;
import com.bartugsevindik.paymentswitch.payment.reconciliation.dto.ReconciliationItemDTO;
import com.bartugsevindik.paymentswitch.payment.reconciliation.dto.ReconciliationRunDTO;
import com.bartugsevindik.paymentswitch.payment.reconciliation.dto.SettlementRecord;
import com.bartugsevindik.paymentswitch.payment.reconciliation.entity.ReconciliationItem;
import com.bartugsevindik.paymentswitch.payment.reconciliation.entity.ReconciliationRun;
import com.bartugsevindik.paymentswitch.payment.reconciliation.enums.ReconciliationResult;
import com.bartugsevindik.paymentswitch.payment.reconciliation.enums.SettlementRecordType;
import com.bartugsevindik.paymentswitch.payment.reconciliation.repository.ReconciliationItemRepository;
import com.bartugsevindik.paymentswitch.payment.reconciliation.repository.ReconciliationRunRepository;
import com.bartugsevindik.paymentswitch.payment.reconciliation.service.ReconciliationService;
import com.bartugsevindik.paymentswitch.payment.repository.PaymentRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationServiceImpl implements ReconciliationService {

    /**
     * Takasa girmesi beklenen ödeme durumları. İade edilmiş satış da takasa girer; iade ayrı bir satırdır.
     */
    private static final Set<PaymentStatus> SETTLED = EnumSet.of(PaymentStatus.APPROVED, PaymentStatus.PARTIALLY_REFUNDED,
            PaymentStatus.REFUNDED);

    private final SettlementFileClient settlementFileClient;
    private final PaymentRepository paymentRepository;
    private final PaymentOperationRepository paymentOperationRepository;
    private final ReconciliationRunRepository reconciliationRunRepository;
    private final ReconciliationItemRepository reconciliationItemRepository;
    private final ReconciliationProperties properties;
    private final PaymentOperationProperties operationProperties;
    private final TransactionTemplate transactionTemplate;
    private final MeterRegistry meterRegistry;

    /**
     * <h1>Mutabakat</h1>
     * <p>İki yönlü karşılaştırma yapılır: bizden bankaya ve bankadan bize. Detay arayüzde.</p>
     *
     * @param bankCode     Banka
     * @param businessDate İş günü
     * @return Özet ve aksiyon gerektiren satırlar
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 29.09.2026 - PS-7
     */
    @Override
    public ReconciliationRunDTO reconcile(@NotNull BankCode bankCode, @NotNull LocalDate businessDate) {
        // Dosyalar transaction dışında alınır; bankanın yavaş SFTP / API'si DB connection tutmasın
        Map<String, SettlementRecord> bankToday = index(settlementFileClient.fetch(bankCode, businessDate));
        Map<String, SettlementRecord> bankAround = new HashMap<>();
        for (int day = 1; day <= properties.getDateToleranceDays(); day++) {
            bankAround.putAll(index(settlementFileClient.fetch(bankCode, businessDate.minusDays(day))));
            bankAround.putAll(index(settlementFileClient.fetch(bankCode, businessDate.plusDays(day))));
        }

        return transactionTemplate.execute(status -> {
            String runId = UUID.randomUUID().toString();
            List<ReconciliationItem> items = new ArrayList<>();
            Set<String> matchedBankKeys = new HashSet<>();

            compareOurSales(runId, bankCode, businessDate, bankToday, bankAround, items, matchedBankKeys);
            compareOurRefunds(runId, bankCode, businessDate, bankToday, bankAround, items, matchedBankKeys);
            compareBankLines(runId, bankToday, items, matchedBankKeys);

            // Satırlar çalışmaya foreign key ile bağlı; önce çalışma yazılır
            ReconciliationRun run = reconciliationRunRepository.save(summarize(runId, bankCode, businessDate, items));
            reconciliationItemRepository.saveAll(items);
            report(run, items);
            return toDto(run, items);
        });
    }

    @Override
    public List<ReconciliationItemDTO> getItems(@NotNull String runId) {
        reconciliationRunRepository.findByRunId(runId).orElseThrow(() -> new NotFoundException("Mutabakat", "runId", runId));
        return reconciliationItemRepository.findByRunIdOrderByIdAsc(runId).stream().map(ReconciliationServiceImpl::toDto).toList();
    }

    private void compareOurSales(String runId, BankCode bankCode, LocalDate date, Map<String, SettlementRecord> bankToday,
                                 Map<String, SettlementRecord> bankAround, List<ReconciliationItem> items, Set<String> matchedBankKeys) {
        List<Payment> payments = paymentRepository.findByBankCodeAndCreatedDateGreaterThanEqualAndCreatedDateLessThan(
                bankCode, dayStart(date), dayStart(date.plusDays(1)));
        for (Payment payment : payments) {
            // Takasa girmemesi gereken ödemeler (red, iptal, cevapsız) bankadan bize yönlü karşılaştırmada ele alınır
            if (!SETTLED.contains(payment.getPaymentStatus())) {
                continue;
            }
            String key = "SALE:" + payment.getPaymentId();
            items.add(match(runId, SettlementRecordType.SALE, payment.getPaymentId(), null, payment.getAmount(),
                    payment.getPaymentStatus().name(), key, bankToday, bankAround, matchedBankKeys));
        }
    }

    private void compareOurRefunds(String runId, BankCode bankCode, LocalDate date, Map<String, SettlementRecord> bankToday,
                                   Map<String, SettlementRecord> bankAround, List<ReconciliationItem> items, Set<String> matchedBankKeys) {
        for (PaymentOperation refund : paymentOperationRepository.findSucceededRefunds(bankCode, dayStart(date), dayStart(date.plusDays(1)))) {
            String key = "REFUND:" + refund.getOperationId();
            items.add(match(runId, SettlementRecordType.REFUND, refund.getPaymentId(), refund.getOperationId(), refund.getAmount(),
                    refund.getStatus().name(), key, bankToday, bankAround, matchedBankKeys));
        }
    }

    private ReconciliationItem match(String runId, SettlementRecordType type, String orderId, String operationId, long ourAmount,
                                     String ourStatus, String key, Map<String, SettlementRecord> bankToday,
                                     Map<String, SettlementRecord> bankAround, Set<String> matchedBankKeys) {
        SettlementRecord bank = Optional.ofNullable(bankToday.get(key)).orElse(bankAround.get(key));
        ReconciliationItem.ReconciliationItemBuilder<?, ?> item = ReconciliationItem.builder()
                .runId(runId).recordType(type).orderId(orderId).operationId(operationId)
                .ourAmount(ourAmount).ourStatus(ourStatus);
        if (bank == null) {
            return item.result(ReconciliationResult.MISSING_IN_BANK)
                    .detail("Bizde başarılı, bankanın dosyalarında yok. Üye işyerine gelmeyecek para ödenebilir.").build();
        }
        matchedBankKeys.add(key);
        item.bankAmount(bank.amount());
        if (bank.amount() != ourAmount) {
            return item.result(ReconciliationResult.AMOUNT_MISMATCH).detail("Tutarlar farklı.").build();
        }
        if (!bankToday.containsKey(key)) {
            return item.result(ReconciliationResult.MATCHED_DIFFERENT_DAY)
                    .detail("Bankanın " + bank.fileDate() + " tarihli dosyasında (gün sonu kayması).").build();
        }
        return item.result(ReconciliationResult.MATCHED).build();
    }

    /**
     * Bankanın dosyasında olup bizim o günkü başarılı kayıtlarımızla eşleşmeyen satırlar.
     */
    private void compareBankLines(String runId, Map<String, SettlementRecord> bankToday, List<ReconciliationItem> items,
                                  Set<String> matchedBankKeys) {
        for (SettlementRecord bank : bankToday.values()) {
            if (matchedBankKeys.contains(bank.key())) {
                continue;
            }
            ReconciliationItem.ReconciliationItemBuilder<?, ?> item = ReconciliationItem.builder()
                    .runId(runId).recordType(bank.type()).orderId(bank.orderId()).operationId(bank.operationId())
                    .bankAmount(bank.amount());
            Optional<String> ourStatus = bank.type() == SettlementRecordType.SALE
                    ? paymentRepository.findByPaymentId(bank.orderId()).map(p -> p.getPaymentStatus().name())
                    : paymentOperationRepository.findByOperationId(bank.operationId()).map(o -> o.getStatus().name());

            if (ourStatus.isEmpty()) {
                items.add(item.result(ReconciliationResult.MISSING_IN_OURS)
                        .detail("Bankada var, bizde hiç kaydı yok. Kart sahibinden para çekilmiş.").build());
            } else if (isSettledStatus(bank.type(), ourStatus.get())) {
                // Bizde başarılı ama farklı bir güne ait; o günün mutabakatında eşleşir, burada tekrar sayılmaz
                continue;
            } else {
                items.add(item.ourStatus(ourStatus.get()).result(ReconciliationResult.STATUS_MISMATCH)
                        .detail("Bizde " + ourStatus.get() + ", bankada başarılı. Kart sahibinden para çekilmiş, üye işyerine bildirilmemiş.")
                        .build());
            }
        }
    }

    private static boolean isSettledStatus(SettlementRecordType type, String status) {
        return type == SettlementRecordType.SALE
                ? SETTLED.contains(PaymentStatus.valueOf(status))
                : PaymentOperationStatus.SUCCEEDED.name().equals(status);
    }

    private ReconciliationRun summarize(String runId, BankCode bankCode, LocalDate date, List<ReconciliationItem> items) {
        Map<ReconciliationResult, Integer> counts = new EnumMap<>(ReconciliationResult.class);
        Arrays.stream(ReconciliationResult.values()).forEach(result -> counts.put(result, 0));
        items.forEach(item -> counts.merge(item.getResult(), 1, Integer::sum));
        return ReconciliationRun.builder()
                .runId(runId).bankCode(bankCode).businessDate(date)
                .matched(counts.get(ReconciliationResult.MATCHED))
                .matchedOtherDay(counts.get(ReconciliationResult.MATCHED_DIFFERENT_DAY))
                .missingInBank(counts.get(ReconciliationResult.MISSING_IN_BANK))
                .missingInOurs(counts.get(ReconciliationResult.MISSING_IN_OURS))
                .amountMismatch(counts.get(ReconciliationResult.AMOUNT_MISMATCH))
                .statusMismatch(counts.get(ReconciliationResult.STATUS_MISMATCH))
                .build();
    }

    private void report(ReconciliationRun run, List<ReconciliationItem> items) {
        items.forEach(item -> meterRegistry.counter("reconciliation.items", "bank", run.getBankCode().name(),
                "result", item.getResult().name()).increment());
        long exceptions = items.stream().filter(item -> item.getResult().isRequiresAction()).count();
        if (exceptions > 0) {
            log.error("Reconciliation has exceptions. bank={}, date={}, runId={}, exceptions={}, missingInOurs={}, statusMismatch={}",
                    run.getBankCode(), run.getBusinessDate(), run.getRunId(), exceptions, run.getMissingInOurs(), run.getStatusMismatch());
        } else {
            log.info("Reconciliation completed clean. bank={}, date={}, matched={}", run.getBankCode(), run.getBusinessDate(),
                    run.getMatched() + run.getMatchedOtherDay());
        }
    }

    /**
     * İş günü İstanbul saatine göredir; DB'deki zamanlar uygulamanın saat diliminde tutulur.
     */
    private LocalDateTime dayStart(LocalDate date) {
        return date.atStartOfDay(operationProperties.getBusinessZone())
                .withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }

    private static Map<String, SettlementRecord> index(List<SettlementRecord> records) {
        Map<String, SettlementRecord> map = new HashMap<>();
        records.forEach(record -> map.put(record.key(), record));
        return map;
    }

    private static ReconciliationRunDTO toDto(ReconciliationRun run, List<ReconciliationItem> items) {
        return new ReconciliationRunDTO(run.getRunId(), run.getBankCode(), run.getBusinessDate(), run.getMatched(),
                run.getMatchedOtherDay(), run.getMissingInBank(), run.getMissingInOurs(), run.getAmountMismatch(),
                run.getStatusMismatch(), items.stream().filter(i -> i.getResult().isRequiresAction()).map(ReconciliationServiceImpl::toDto).toList());
    }

    private static ReconciliationItemDTO toDto(ReconciliationItem i) {
        return new ReconciliationItemDTO(i.getRecordType(), i.getOrderId(), i.getOperationId(), i.getResult(),
                amount(i.getOurAmount()), amount(i.getBankAmount()), i.getOurStatus(), i.getDetail());
    }

    private static BigDecimal amount(Long minor) {
        return minor == null ? null : Money.ofMinor(minor, "TRY").toDecimal();
    }
}
