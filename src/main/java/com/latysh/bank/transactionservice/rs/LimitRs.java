package com.latysh.bank.transactionservice.rs;

import com.latysh.bank.transactionservice.entity.ExpenseCategory;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Schema(description = "Сохранённый лимит")
public record LimitRs(
    @Schema(description = "Идентификатор лимита", example = "1")
    Long id,

    @Schema(description = "Счёт клиента", example = "0000000123")
    String accountFrom,

    @Schema(description = "Категория расхода")
    ExpenseCategory expenseCategory,

    @Schema(description = "Сумма лимита в USD", example = "1000.00")
    BigDecimal limitSum,

    @Schema(description = "Дата и время установления лимита", example = "2022-01-01T00:00:00+03:00")
    OffsetDateTime limitDatetime,

    @Schema(description = "Валюта лимита, всегда USD", example = "USD")
    String limitCurrencyShortname
) {
}
