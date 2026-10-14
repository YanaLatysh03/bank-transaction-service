package com.latysh.bank.transactionservice.rates;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

public interface ExchangeRateClient {
    Optional<BigDecimal> getExchangeRates(String base, String quote, LocalDate date);
}
