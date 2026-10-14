package com.latysh.bank.transactionservice.rq;

import com.latysh.bank.transactionservice.entity.ExpenseCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

@Schema(description = "Новый лимит. Дата устанавливается сервером")
public record CreateLimitRq(
    @Schema(description = "Счёт клиента, 10 цифр", example = "0000000123")
    @NotBlank @Pattern(regexp = "\\d{10}") String accountFrom,

    @Schema(description = "Категория, для которой действует лимит")
    @NotNull ExpenseCategory expenseCategory,

    @Schema(description = "Сумма лимита в USD", example = "1000.00")
    @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal limitSum
) {
}
