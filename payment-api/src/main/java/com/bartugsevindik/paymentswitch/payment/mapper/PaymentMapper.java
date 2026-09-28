/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.mapper;

import com.bartugsevindik.paymentswitch.common.model.Money;
import com.bartugsevindik.paymentswitch.payment.dto.PaymentCreateRequest;
import com.bartugsevindik.paymentswitch.payment.dto.PaymentDTO;
import com.bartugsevindik.paymentswitch.payment.entity.Payment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE, imports = Money.class)
public interface PaymentMapper {

    @Mapping(target = "amount", expression = "java(entity.getMoney().toDecimal())")
    @Mapping(target = "maskedCardNumber", expression = "java(maskCardNumber(entity.getCardBin(), entity.getCardLast4()))")
    PaymentDTO toDto(Payment entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "paymentId", ignore = true)
    @Mapping(target = "paymentStatus", ignore = true)
    @Mapping(target = "amount", expression = "java(Money.of(request.getAmount(), request.getCurrency()).amount())")
    @Mapping(target = "cardBin", source = "cardNumber", qualifiedByName = "extractBin")
    @Mapping(target = "cardLast4", source = "cardNumber", qualifiedByName = "extractLast4")
    Payment toEntity(PaymentCreateRequest request);

    /**
     * 16 hane ve üzeri kartlarda 8 haneli BIN, daha kısa kartlarda (örn. Amex 15 hane) 6 haneli BIN alınır.
     */
    @Named("extractBin")
    default String extractBin(String cardNumber) {
        return cardNumber.substring(0, cardNumber.length() >= 16 ? 8 : 6);
    }

    @Named("extractLast4")
    default String extractLast4(String cardNumber) {
        return cardNumber.substring(cardNumber.length() - 4);
    }

    default String maskCardNumber(String bin, String last4) {
        return bin.substring(0, 6) + "******" + last4;
    }
}
