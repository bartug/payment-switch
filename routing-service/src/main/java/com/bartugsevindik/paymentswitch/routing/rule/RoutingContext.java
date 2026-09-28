/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.routing.rule;

import com.bartugsevindik.paymentswitch.routing.dto.AcquirerBankInfo;
import com.bartugsevindik.paymentswitch.routing.dto.BinInfo;

import java.util.List;
import java.util.Optional;

/**
 * <h1>RoutingContext</h1>
 * <p>Kuralların karar vermek için ihtiyaç duyduğu her şey. Kurallar DB'ye ya da Kafka'ya gitmez,
 * sadece bu nesneye bakar; bu sayede unit test ile denenebilirler.</p>
 *
 * @param installmentCount Taksit sayısı. 1 = tek çekim
 * @param binInfo          BIN çözümlemesi. BIN tanımsızsa boş.
 * @param activeBanks      Şu an işlem alabilen bankalar
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-4
 */
public record RoutingContext(int installmentCount, Optional<BinInfo> binInfo, List<AcquirerBankInfo> activeBanks) {

    public boolean isInstallment() {
        return installmentCount > 1;
    }
}
