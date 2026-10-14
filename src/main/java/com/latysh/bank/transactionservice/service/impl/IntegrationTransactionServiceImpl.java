package com.latysh.bank.transactionservice.service.impl;

import com.latysh.bank.transactionservice.rq.IncomingTransactionRq;
import com.latysh.bank.transactionservice.service.ExchangeRateService;
import com.latysh.bank.transactionservice.service.IntegrationTransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class IntegrationTransactionServiceImpl implements IntegrationTransactionService {
    static public final ZoneId MONTH_ZONE = ZoneId.of("Europe/Minsk");

    private final ExchangeRateService exchangeRates;
    private final BankTransactionService store;

    @Override
    public long accept(IncomingTransactionRq request) {
        LocalDate rateDate = request.datetime().atZoneSameInstant(MONTH_ZONE).toLocalDate();
        BigDecimal sumUsd = exchangeRates.toUsd(request.currencyShortname(), request.sum(), rateDate);
        return store.acceptTransaction(request, sumUsd);
    }
}
