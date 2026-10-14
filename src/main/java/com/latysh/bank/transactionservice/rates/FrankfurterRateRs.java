package com.latysh.bank.transactionservice.rates;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FrankfurterRateRs(
    LocalDate date,
    String base,
    String quote,
    BigDecimal rate
) {
}
