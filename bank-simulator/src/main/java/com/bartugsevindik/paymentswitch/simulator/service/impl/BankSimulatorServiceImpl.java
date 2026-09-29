/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.simulator.service.impl;

import com.bartugsevindik.paymentswitch.common.enums.BankCode;
import com.bartugsevindik.paymentswitch.simulator.dto.BankAuthorizeRequest;
import com.bartugsevindik.paymentswitch.simulator.dto.BankOperationRequest;
import com.bartugsevindik.paymentswitch.simulator.dto.BankTransactionResponse;
import com.bartugsevindik.paymentswitch.simulator.dto.ChaosSettings;
import com.bartugsevindik.paymentswitch.simulator.model.SimulatedTransaction;
import com.bartugsevindik.paymentswitch.simulator.service.BankSimulatorService;
import com.bartugsevindik.paymentswitch.simulator.service.BankUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
public class BankSimulatorServiceImpl implements BankSimulatorService {

    private final Map<String, SimulatedTransaction> transactions = new ConcurrentHashMap<>();
    private final Map<BankCode, ChaosSettings> chaos = new ConcurrentHashMap<>();
    // bank:orderId -> (refundId -> tutar)
    private final Map<String, Map<String, Long>> refunds = new ConcurrentHashMap<>();

    /**
     * <h1>Satış</h1>
     * <p>Aynı orderId ile gelen ikinci istek yeni işlem oluşturmaz, ilk sonucu döner. Tutarın son iki hanesi sonucu
     * belirler: 51 yetersiz bakiye, 05 onaylanmadı, 54 süresi dolmuş kart, diğerleri onay.</p>
     *
     * @param bankCode Banka
     * @param request  Satış isteği
     * @return İşlem sonucu
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-5
     */
    @Override
    public BankTransactionResponse authorize(@NotNull BankCode bankCode, @NotNull BankAuthorizeRequest request) {
        ChaosSettings settings = getChaos(bankCode);
        applyLatencyAndFailures(bankCode, settings);

        // computeIfAbsent atomik: aynı orderId ile eş zamanlı iki istek gelse de tek işlem oluşur
        SimulatedTransaction transaction = transactions.computeIfAbsent(key(bankCode, request.orderId()),
                k -> decide(request));
        log.info("Bank authorize. bank={}, orderId={}, status={}, responseCode={}",
                bankCode, request.orderId(), transaction.status(), transaction.responseCode());

        if (random() < settings.lateResponseRate()) {
            log.warn("Chaos: late response after processing. bank={}, orderId={}, delayMs={}",
                    bankCode, request.orderId(), settings.lateResponseMs());
            sleep(settings.lateResponseMs());
        }
        return transaction.toResponse();
    }

    /**
     * <h1>İşlem Sorgulama</h1>
     *
     * @param bankCode Banka
     * @param orderId  Sipariş numarası
     * @return İşlem. Banka işlemi hiç almadıysa boş.
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-5
     */
    @Override
    public Optional<BankTransactionResponse> inquire(@NotNull BankCode bankCode, @NotNull String orderId) {
        applyLatencyAndFailures(bankCode, getChaos(bankCode));
        return Optional.ofNullable(transactions.get(key(bankCode, orderId))).map(SimulatedTransaction::toResponse);
    }

    /**
     * <h1>Teknik İptal (Reversal)</h1>
     * <p>İşlem yoksa da orderId iptal olarak işaretlenir; asıl istek bankaya sonradan ulaşırsa reddedilir.</p>
     *
     * @param bankCode Banka
     * @param orderId  Sipariş numarası
     * @return İptal sonucu
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-5
     */
    @Override
    public BankTransactionResponse reverse(@NotNull BankCode bankCode, @NotNull String orderId) {
        applyLatencyAndFailures(bankCode, getChaos(bankCode));
        SimulatedTransaction reversed = transactions.compute(key(bankCode, orderId), (k, existing) -> existing == null
                ? new SimulatedTransaction(orderId, 0, "REVERSED", "00", null, null, "İşlem bulunamadı, sipariş numarası iptal edildi")
                : existing.reversed());
        log.info("Bank reversal. bank={}, orderId={}", bankCode, orderId);
        return reversed.toResponse();
    }

    /**
     * <h1>İptal (Void)</h1>
     * <p>Sadece onaylı ve iade yapılmamış işlem iptal edilebilir. Tekrar gelen istek aynı sonucu döner.</p>
     *
     * @param bankCode Banka
     * @param orderId  Sipariş numarası
     * @return İptal sonucu
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 29.09.2026 - PS-6
     */
    @Override
    public BankTransactionResponse voidTransaction(@NotNull BankCode bankCode, @NotNull String orderId) {
        applyLatencyAndFailures(bankCode, getChaos(bankCode));
        String key = key(bankCode, orderId);
        SimulatedTransaction existing = transactions.get(key);
        if (existing == null) {
            return new BankTransactionResponse(orderId, "DECLINED", "12", null, null, "İşlem bulunamadı");
        }
        if ("VOIDED".equals(existing.status())) {
            return existing.toResponse();
        }
        if (!"APPROVED".equals(existing.status()) || refunds.containsKey(key)) {
            return new BankTransactionResponse(orderId, "DECLINED", "12", null, existing.rrn(), "İşlem iptal edilemez");
        }
        SimulatedTransaction voided = existing.withStatus("VOIDED", "İşlem iptal edildi");
        transactions.put(key, voided);
        log.info("Bank void. bank={}, orderId={}", bankCode, orderId);
        return voided.toResponse();
    }

    /**
     * <h1>İade (Refund)</h1>
     * <p>Kısmi iade yapılabilir; toplam iade satış tutarını aşamaz. Aynı refundId ikinci kez iade oluşturmaz.</p>
     *
     * @param bankCode Banka
     * @param orderId  Sipariş numarası
     * @param request  İade isteği
     * @return İade sonucu
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 29.09.2026 - PS-6
     */
    @Override
    public synchronized BankTransactionResponse refund(@NotNull BankCode bankCode, @NotNull String orderId,
                                                       @NotNull BankOperationRequest request) {
        applyLatencyAndFailures(bankCode, getChaos(bankCode));
        String key = key(bankCode, orderId);
        SimulatedTransaction existing = transactions.get(key);
        if (existing == null || !"APPROVED".equals(existing.status())) {
            return new BankTransactionResponse(orderId, "DECLINED", "12", null, null, "İade yapılabilecek onaylı işlem yok");
        }
        Map<String, Long> orderRefunds = refunds.computeIfAbsent(key, k -> new ConcurrentHashMap<>());
        if (orderRefunds.containsKey(request.operationId())) {
            return new BankTransactionResponse(orderId, "REFUNDED", "00", null, existing.rrn(), "İade daha önce yapıldı");
        }
        long refunded = orderRefunds.values().stream().mapToLong(Long::longValue).sum();
        if (refunded + request.amount() > existing.amount()) {
            return new BankTransactionResponse(orderId, "DECLINED", "13", null, existing.rrn(), "İade tutarı satış tutarını aşıyor");
        }
        orderRefunds.put(request.operationId(), request.amount());
        log.info("Bank refund. bank={}, orderId={}, refundId={}, amount={}", bankCode, orderId, request.operationId(), request.amount());
        return new BankTransactionResponse(orderId, "REFUNDED", "00", null, existing.rrn(), "İade edildi");
    }

    /**
     * <h1>Echo</h1>
     * <p>ISO 8583 0800 ağ yönetimi mesajının karşılığı. Banka ayakta mı?</p>
     *
     * @param bankCode Banka
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-5
     */
    @Override
    public void echo(@NotNull BankCode bankCode) {
        if (getChaos(bankCode).down()) {
            throw new BankUnavailableException(bankCode + " kapalı");
        }
    }

    @Override
    public ChaosSettings getChaos(@NotNull BankCode bankCode) {
        return chaos.getOrDefault(bankCode, ChaosSettings.none());
    }

    @Override
    public Map<BankCode, ChaosSettings> getAllChaos() {
        Map<BankCode, ChaosSettings> all = new EnumMap<>(BankCode.class);
        for (BankCode bank : BankCode.values()) {
            all.put(bank, getChaos(bank));
        }
        return all;
    }

    @Override
    public void updateChaos(@NotNull BankCode bankCode, @NotNull ChaosSettings settings) {
        chaos.put(bankCode, settings);
        log.warn("Chaos settings changed. bank={}, settings={}", bankCode, settings);
    }

    private void applyLatencyAndFailures(BankCode bankCode, ChaosSettings settings) {
        if (settings.down()) {
            throw new BankUnavailableException(bankCode + " kapalı");
        }
        sleep(settings.latencyMs());
        if (random() < settings.failureRate()) {
            throw new BankUnavailableException(bankCode + " geçici olarak hizmet veremiyor");
        }
    }

    private static SimulatedTransaction decide(BankAuthorizeRequest request) {
        YearMonth expiry = YearMonth.of(2000 + Integer.parseInt(request.expiryYear()), Integer.parseInt(request.expiryMonth()));
        if (expiry.isBefore(YearMonth.now())) {
            return declined(request, "54", "Kartın süresi dolmuş");
        }
        return switch ((int) (request.amount() % 100)) {
            case 51 -> declined(request, "51", "Yetersiz bakiye");
            case 5 -> declined(request, "05", "İşlem onaylanmadı");
            case 54 -> declined(request, "54", "Kartın süresi dolmuş");
            default -> new SimulatedTransaction(request.orderId(), request.amount(), "APPROVED", "00",
                    digits(6), digits(12), "Onaylandı");
        };
    }

    private static SimulatedTransaction declined(BankAuthorizeRequest request, String code, String message) {
        return new SimulatedTransaction(request.orderId(), request.amount(), "DECLINED", code, null, digits(12), message);
    }

    private static String digits(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ThreadLocalRandom.current().nextInt(10));
        }
        return sb.toString();
    }

    private static String key(BankCode bankCode, String orderId) {
        return bankCode + ":" + orderId;
    }

    private static double random() {
        return ThreadLocalRandom.current().nextDouble();
    }

    private static void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
