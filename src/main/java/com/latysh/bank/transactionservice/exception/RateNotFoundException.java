package com.latysh.bank.transactionservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "Курс не найден ни в БД, ни во внешнем API")
public class RateNotFoundException extends RuntimeException{
}
