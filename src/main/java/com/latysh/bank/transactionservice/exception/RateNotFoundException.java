package com.latysh.bank.transactionservice.exception;

/**
 * Курса к USD нет ни за запрошенный день, ни среди прошлых закрытий.
 * На API это 404 в формате ProblemDetail, см. {@link ApiExceptionHandler}.
 */
public class RateNotFoundException extends RuntimeException {

    public RateNotFoundException() {
        super("Курс не найден ни в базе, ни у внешнего источника");
    }
}
