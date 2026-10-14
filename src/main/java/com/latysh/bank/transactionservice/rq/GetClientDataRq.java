package com.latysh.bank.transactionservice.rq;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "Получение пользовательских данных о лимитах и превышающих лимит транзакциях")
public record GetClientDataRq(
    @Schema(description = "Счёт клиента, 10 цифр", example = "0000000123")
    @NotBlank
    @Pattern(regexp = "\\d{10}") String accountFrom
) {
}
