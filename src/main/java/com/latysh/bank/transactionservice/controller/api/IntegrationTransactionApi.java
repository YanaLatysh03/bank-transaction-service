package com.latysh.bank.transactionservice.controller.api;

import com.latysh.bank.transactionservice.rq.IncomingTransactionRq;
import com.latysh.bank.transactionservice.rs.AcceptedTransactionRs;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

@Tag(name = "Интеграция с банком", description = "Приём расходных операций в реальном времени")
@RequestMapping("/api/integration/transactions")
public interface IntegrationTransactionApi {

    @Operation(
            summary = "Принять расходную операцию",
            description = "Сохраняет операцию и сразу ставит limit_exceeded. Флаг в ответ не возвращается."
    )
    @ApiResponse(responseCode = "201", description = "Операция сохранена")
    @ApiResponse(responseCode = "400", description = "Тело запроса не прошло проверку",
        content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
            schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "Курс к USD не найден ни в базе, ни у внешнего источника",
        content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
            schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AcceptedTransactionRs accept(@Valid @RequestBody IncomingTransactionRq request);
}
