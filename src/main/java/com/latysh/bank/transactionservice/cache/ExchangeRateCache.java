package com.latysh.bank.transactionservice.cache;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

public interface ExchangeRateCache {
    Optional<BigDecimal> get(String base, String quote, LocalDate day);

    void put(String base, String quote, LocalDate day, BigDecimal close);
}
