package com.latysh.bank.transactionservice.rs;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Подтверждение, что операция сохранена")
public record AcceptedTransactionRs(
    @Schema(description = "Идентификатор сохранённой операции", example = "1")
    long id
) {
}
