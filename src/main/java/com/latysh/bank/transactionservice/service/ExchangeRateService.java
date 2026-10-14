package com.latysh.bank.transactionservice.service;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface ExchangeRateService {
    BigDecimal toUsd(String currency, BigDecimal sum, LocalDate day);
}
