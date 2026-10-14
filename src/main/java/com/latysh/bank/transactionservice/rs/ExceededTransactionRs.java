package com.latysh.bank.transactionservice.rs;

import com.latysh.bank.transactionservice.entity.ExpenseCategory;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Schema(description = "Операция, превысившая лимит, вместе с лимитом, который действовал в этот момент")
public record ExceededTransactionRs(
    @Schema(description = "Счёт клиента", example = "0000000123")
    String accountFrom,

    @Schema(description = "Счёт контрагента", example = "9999999999")
    String accountTo,

    @Schema(description = "Валюта операции", example = "KZT")
    String currencyShortname,

    @Schema(description = "Сумма операции", example = "600.00")
    BigDecimal sum,

    @Schema(description = "Категория расхода")
    ExpenseCategory expenseCategory,

    @Schema(description = "Дата и время операции", example = "2022-01-03T10:15:00+06:00")
    OffsetDateTime datetime,

    @Schema(description = "Сумма лимита, который был превышен", example = "1000.00")
    BigDecimal limitSum,

    @Schema(description = "Дата установления этого лимита. Пусто, если действовали неявные 1000 USD", example = "2022-01-01T00:00:00+06:00")
    OffsetDateTime limitDatetime,

    @Schema(description = "Валюта лимита", example = "USD")
    String limitCurrencyShortname
) {
}
