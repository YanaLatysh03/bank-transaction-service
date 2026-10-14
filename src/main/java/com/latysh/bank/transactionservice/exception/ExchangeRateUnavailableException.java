package com.latysh.bank.transactionservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.time.LocalDate;

@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class ExchangeRateUnavailableException extends Exception{

    public ExchangeRateUnavailableException(String currency, LocalDate day) {
        super("No USD close for " + currency + " on " + day);
    }
}
