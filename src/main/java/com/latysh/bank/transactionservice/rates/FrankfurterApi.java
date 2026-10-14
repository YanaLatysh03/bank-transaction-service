package com.latysh.bank.transactionservice.rates;

import feign.Param;
import feign.RequestLine;

import java.time.LocalDate;

public interface FrankfurterApi {

    @RequestLine("GET /v2/rate/{base}/{quote}?date={date}")
    FrankfurterRateRs getRate(@Param("base") String base,
                              @Param("quote") String quote,
                              @Param("date") LocalDate date);
}
