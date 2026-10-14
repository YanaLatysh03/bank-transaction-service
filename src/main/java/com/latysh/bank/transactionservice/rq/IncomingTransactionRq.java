package com.latysh.bank.transactionservice.rq;

import com.latysh.bank.transactionservice.entity.ExpenseCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Schema(description = "Расходная операция от банка")
public record IncomingTransactionRq(
    @Schema(description = "Счёт клиента, 10 цифр", example = "0000000123")
    @NotBlank @Pattern(regexp = "\\d{10}") String accountFrom,

    @Schema(description = "Счёт контрагента, 10 цифр", example = "9999999999")
    @NotBlank @Pattern(regexp = "\\d{10}") String accountTo,

    @Schema(description = "Валюта операции, код ISO 4217", example = "KZT")
    @NotBlank @Pattern(regexp = "[A-Z]{3}") String currencyShortname,

    @Schema(description = "Сумма операции", example = "10000.45")
    @NotNull @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal sum,

    @Schema(description = "Категория расхода")
    @NotNull ExpenseCategory expenseCategory,

    @Schema(description = "Дата и время операции с часовым поясом", example = "2022-01-30T00:00:00+06:00")
    @NotNull OffsetDateTime datetime
) {
}
