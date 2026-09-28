/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.routing.rule;

import com.bartugsevindik.paymentswitch.common.enums.BankCode;
import com.bartugsevindik.paymentswitch.routing.dto.AcquirerBankInfo;
import com.bartugsevindik.paymentswitch.routing.dto.BinInfo;
import com.bartugsevindik.paymentswitch.routing.enums.CardProgram;
import com.bartugsevindik.paymentswitch.routing.enums.CardScheme;
import com.bartugsevindik.paymentswitch.routing.enums.CardType;
import com.bartugsevindik.paymentswitch.routing.enums.RoutingReason;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kurallar Spring'siz, saf Java olarak test edilir. Oranlar seed verisiyle aynı.
 */
class RoutingEngineTest {

    private static final AcquirerBankInfo QNB = new AcquirerBankInfo(BankCode.QNB, true, true, 170, 199);
    private static final AcquirerBankInfo YKB = new AcquirerBankInfo(BankCode.YKB, true, true, 180, 220);
    private static final AcquirerBankInfo GARANTI = new AcquirerBankInfo(BankCode.GARANTI, true, true, 175, 215);
    private static final AcquirerBankInfo ISBANK = new AcquirerBankInfo(BankCode.ISBANK, true, true, 185, 225);
    private static final AcquirerBankInfo AKBANK = new AcquirerBankInfo(BankCode.AKBANK, true, true, 190, 230);
    private static final List<AcquirerBankInfo> ALL_BANKS = List.of(QNB, YKB, GARANTI, ISBANK, AKBANK);

    private static final BinInfo WORLD_CREDIT = bin(BankCode.YKB, CardProgram.WORLD, CardType.CREDIT);
    private static final BinInfo BONUS_CREDIT = bin(BankCode.GARANTI, CardProgram.BONUS, CardType.CREDIT);
    private static final BinInfo ISBANK_DEBIT = bin(BankCode.ISBANK, null, CardType.DEBIT);

    private final RoutingEngine engine = new RoutingEngine(List.of(
            new UnknownBinInstallmentRule(),
            new NonCreditInstallmentRule(),
            new InstallmentProgramRule(),
            new LowestCostRule()));

    @Test
    void taksitliIslemProgramBankasinaGider() {
        RoutingResult result = engine.decide(context(3, WORLD_CREDIT, ALL_BANKS));

        assertThat(result.reason()).isEqualTo(RoutingReason.ON_US_INSTALLMENT);
        assertThat(result.bankCode()).isEqualTo(BankCode.YKB);
        assertThat(result.onUs()).isTrue();
    }

    @Test
    void taksitliIslemdeDahaUcuzBankaOlsaBileProgramBankasiSecilir() {
        // QNB'nin off-us oranı (199) YKB'nin on-us oranından (180) yüksek, ama taksitte maliyete hiç bakılmaz
        RoutingResult result = engine.decide(context(6, BONUS_CREDIT, ALL_BANKS));

        assertThat(result.bankCode()).isEqualTo(BankCode.GARANTI);
    }

    @Test
    void programBankasiKapaliysaTaksitliIslemReddedilir() {
        RoutingResult result = engine.decide(context(3, WORLD_CREDIT, List.of(QNB, GARANTI, ISBANK, AKBANK)));

        assertThat(result.isRouted()).isFalse();
        assertThat(result.reason()).isEqualTo(RoutingReason.PROGRAM_BANK_UNAVAILABLE);
    }

    @Test
    void tekCekimdeKartinKendiBankasiOnUsOlarakKazanir() {
        RoutingResult result = engine.decide(context(1, WORLD_CREDIT, ALL_BANKS));

        // YKB on-us 180 < QNB off-us 199
        assertThat(result.reason()).isEqualTo(RoutingReason.LOWEST_COST);
        assertThat(result.bankCode()).isEqualTo(BankCode.YKB);
        assertThat(result.onUs()).isTrue();
    }

    @Test
    void tekCekimdeKartinBankasiKapaliysaEnUcuzBankayaFailover() {
        RoutingResult result = engine.decide(context(1, WORLD_CREDIT, List.of(QNB, GARANTI, ISBANK, AKBANK)));

        assertThat(result.isRouted()).isTrue();
        assertThat(result.bankCode()).isEqualTo(BankCode.QNB);
        assertThat(result.onUs()).isFalse();
    }

    @Test
    void tanimsizBinTekCekimdeEnUcuzOffUsBankayaGider() {
        RoutingResult result = engine.decide(new RoutingContext(1, Optional.empty(), ALL_BANKS));

        assertThat(result.bankCode()).isEqualTo(BankCode.QNB);
        assertThat(result.onUs()).isFalse();
    }

    @Test
    void tanimsizBinIleTaksitYapilamaz() {
        RoutingResult result = engine.decide(new RoutingContext(3, Optional.empty(), ALL_BANKS));

        assertThat(result.reason()).isEqualTo(RoutingReason.UNKNOWN_BIN_INSTALLMENT);
    }

    @Test
    void bankaKartiIleTaksitYapilamaz() {
        RoutingResult result = engine.decide(context(6, ISBANK_DEBIT, ALL_BANKS));

        assertThat(result.reason()).isEqualTo(RoutingReason.NON_CREDIT_INSTALLMENT);
    }

    @Test
    void bankaKartiTekCekimCalisir() {
        RoutingResult result = engine.decide(context(1, ISBANK_DEBIT, ALL_BANKS));

        // İş Bankası on-us 185 < QNB off-us 199
        assertThat(result.bankCode()).isEqualTo(BankCode.ISBANK);
    }

    @Test
    void programiOlmayanKrediKartiIleTaksitYapilamaz() {
        BinInfo noProgram = bin(BankCode.YKB, null, CardType.CREDIT);

        assertThat(engine.decide(context(3, noProgram, ALL_BANKS)).reason()).isEqualTo(RoutingReason.NO_INSTALLMENT_PROGRAM);
    }

    @Test
    void hicAktifBankaYoksaReddedilir() {
        assertThat(engine.decide(context(1, WORLD_CREDIT, List.of())).reason()).isEqualTo(RoutingReason.NO_ACTIVE_BANK);
    }

    @Test
    void esitKomisyondaOnUsBankaTercihEdilir() {
        AcquirerBankInfo cheapOffUs = new AcquirerBankInfo(BankCode.QNB, true, true, 150, 180);

        RoutingResult result = engine.decide(context(1, WORLD_CREDIT, List.of(cheapOffUs, YKB)));

        assertThat(result.bankCode()).isEqualTo(BankCode.YKB);
    }

    private static RoutingContext context(int installment, BinInfo bin, List<AcquirerBankInfo> banks) {
        return new RoutingContext(installment, Optional.of(bin), banks);
    }

    private static BinInfo bin(BankCode issuer, CardProgram program, CardType type) {
        return new BinInfo("000000", issuer, program, CardScheme.VISA, type, false);
    }
}
