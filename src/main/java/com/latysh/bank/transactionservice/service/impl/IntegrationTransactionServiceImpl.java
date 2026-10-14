package com.latysh.bank.transactionservice.service.impl;

import com.latysh.bank.transactionservice.rq.IncomingTransactionRq;
import com.latysh.bank.transactionservice.service.BankTransactionService;
import com.latysh.bank.transactionservice.service.ExchangeRateService;
import com.latysh.bank.transactionservice.service.IntegrationTransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;

@Slf4j
@Service
@RequiredArgsConstructor
public class IntegrationTransactionServiceImpl implements IntegrationTransactionService {
    static public final ZoneId MONTH_ZONE = ZoneId.of("Europe/Minsk");

    private final ExchangeRateService exchangeRates;
    private final BankTransactionService store;

    @Override
    public long accept(IncomingTransactionRq request) {
        LocalDate rateDate = request.datetime().atZoneSameInstant(MONTH_ZONE).toLocalDate();
        log.info("Accepting transaction from {} to {} of {} {} on {}",
            request.accountFrom(), request.accountTo(), request.sum(), request.currencyShortname(), rateDate);
        BigDecimal sumUsd = exchangeRates.toUsd(request.currencyShortname(), request.sum(), rateDate);
        long id = store.acceptTransaction(request, sumUsd);
        log.info("Accepted transaction {}, sum in USD {}", id, sumUsd);
        return id;
    }
}
