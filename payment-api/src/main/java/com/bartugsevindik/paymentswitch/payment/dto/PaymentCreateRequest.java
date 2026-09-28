/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.validator.constraints.CreditCardNumber;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "PaymentCreateRequest", description = "POS terminalinden gelen ödeme isteği. Üye işyeri ve terminal bilgisi imzalı header'lardan alınır.")
public class PaymentCreateRequest {

    @NotNull
    @DecimalMin(value = "0.01", message = "Tutar sıfırdan büyük olmalıdır.")
    @Digits(integer = 12, fraction = 2)
    @Schema(description = "İşlem tutarı", example = "1250.50")
    private BigDecimal amount;

    @NotBlank
    @Pattern(regexp = "^[A-Z]{3}$", message = "Para birimi ISO 4217 formatında olmalıdır.")
    @Schema(description = "Para birimi", example = "TRY")
    private String currency;

    @NotNull
    @Min(1)
    @Max(12)
    @Schema(description = "Taksit sayısı. 1 = tek çekim", example = "3")
    private Integer installmentCount;

    @NotBlank
    @CreditCardNumber(message = "Kart numarası geçersiz.")
    @ToString.Exclude
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Schema(description = "Kart numarası", example = "5400617020092306")
    private String cardNumber;

    @NotBlank
    @Pattern(regexp = "^(0[1-9]|1[0-2])$", message = "Son kullanma ayı 01-12 arasında olmalıdır.")
    @ToString.Exclude
    @Schema(description = "Son kullanma ayı", example = "12")
    private String expiryMonth;

    @NotBlank
    @Pattern(regexp = "^\\d{2}$", message = "Son kullanma yılı 2 haneli olmalıdır.")
    @ToString.Exclude
    @Schema(description = "Son kullanma yılı", example = "28")
    private String expiryYear;

    @Pattern(regexp = "^\\d{3,4}$", message = "CVV 3 veya 4 haneli olmalıdır.")
    @ToString.Exclude
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Schema(description = "CVV. Sanal POS'ta zorunlu, fiziki POS'ta çip kullanıldığı için gönderilmez.", example = "000")
    private String cvv;
}
