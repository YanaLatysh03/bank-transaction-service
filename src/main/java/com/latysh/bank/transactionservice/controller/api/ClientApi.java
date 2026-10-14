package com.latysh.bank.transactionservice.controller.api;

import com.latysh.bank.transactionservice.rq.CreateLimitRq;
import com.latysh.bank.transactionservice.rq.GetClientDataRq;
import com.latysh.bank.transactionservice.rs.ExceededTransactionRs;
import com.latysh.bank.transactionservice.rs.LimitRs;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Клиент", description = "Работа с лимитами и операциями, превысившими лимит")
@RequestMapping("/api/client")
public interface ClientApi {

    @Operation(
            summary = "Установить новый лимит",
            description = "Создаёт новую строку лимита. Уже сохранённый лимит не изменяется. " +
                "Дату ставит сервер, прошлое и будущее с клиента не принимаются."
    )
    @ApiResponse(responseCode = "201", description = "Лимит сохранён")
    @ApiResponse(responseCode = "400", description = "Тело запроса не прошло проверку", content = @Content)
    @PostMapping("/limits")
    @ResponseStatus(HttpStatus.CREATED)
    LimitRs createLimit(@Valid @RequestBody CreateLimitRq request);

    @Operation(
            summary = "Получить все лимиты",
            description = "История лимитов, а не одна действующая запись."
    )
    @ApiResponse(responseCode = "200", description = "Список лимитов")
    @ApiResponse(responseCode = "400", description = "Тело запроса не прошло проверку", content = @Content)
    @GetMapping("/limits/{accountFrom}")
    List<LimitRs> findLimits(@PathVariable @Valid String accountFrom);

    @Operation(
            summary = "Получить операции, превысившие лимит",
            description = "Транзакции с limit_exceeded = true и лимит, который действовал в момент операции: сумма, дата установления и валюта USD."
    )
    @ApiResponse(responseCode = "200", description = "Список превышений")
    @ApiResponse(responseCode = "400", description = "Тело запроса не прошло проверку", content = @Content)
    @GetMapping("transactions/{accountFrom}/exceeded")
    List<ExceededTransactionRs> findLimitExceededTransactions(@PathVariable @Valid String accountFrom);
}
