package com.latysh.bank.transactionservice.service.impl;

import com.latysh.bank.transactionservice.cache.ExchangeRateCache;
import com.latysh.bank.transactionservice.entity.ExchangeRate;
import com.latysh.bank.transactionservice.exception.ExchangeRateUnavailableException;
import com.latysh.bank.transactionservice.exception.RateNotFoundException;
import com.latysh.bank.transactionservice.rates.ExchangeRateClient;
import com.latysh.bank.transactionservice.repository.ExchangeRateRepository;
import com.latysh.bank.transactionservice.service.ExchangeRateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeRateServiceImpl implements ExchangeRateService {
    private static final String USD = "USD";
    private final ExchangeRateRepository exchangeRateRepository;
    private final ExchangeRateClient client;
    private final ExchangeRateCache exchangeRateCache;

    @Override
    public BigDecimal toUsd(String currency, BigDecimal sum, LocalDate day) {
        return sum.multiply(usdPerUnit(currency, day)).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal usdPerUnit(String currency, LocalDate day) {
        if (USD.equals(currency)) {
            return BigDecimal.ONE;
        }
        return exchangeRateCache.get(currency, USD, day)
            .orElseGet(() -> loadUsdPerUnit(currency, day));
    }

    private BigDecimal loadUsdPerUnit(String currency, LocalDate day) {
        return exchangeRateRepository.findByBaseAndQuoteAndRateDate(currency, USD, day)
            .map(rate -> remember(currency, day, rate.getClose()))
            .orElseGet(() -> {
                try {
                    return fetchAndStore(currency, day);
                } catch (RateNotFoundException notFound) {
                    throw notFound;
                } catch (Exception unavailable) {
                    // API не отдал курс этого дня (выходной или ошибка) — берём последнее закрытие.
                    // В кэш его не кладём: это не курс запрошенного дня, следующий вызов снова спросит API.
                    log.warn("Exchange rate for {} on {} is unavailable, using the latest stored close",
                        currency, day, unavailable);
                    return exchangeRateRepository
                        .findFirstByBaseAndQuoteAndRateDateLessThanOrderByRateDateDesc(currency, USD, day)
                        .map(ExchangeRate::getClose)
                        .orElseThrow(RateNotFoundException::new);
                }
            });
    }

    private BigDecimal fetchAndStore(String currency, LocalDate day) throws ExchangeRateUnavailableException {
        log.info("Fetching exchange rate for {}/USD on {}", currency, day);
        BigDecimal close = client.getExchangeRates(currency, USD, day)
            .orElseThrow(() -> new ExchangeRateUnavailableException(currency, day));
        try {
            exchangeRateRepository.saveAndFlush(new ExchangeRate(currency, USD, day, close));
        } catch (DataIntegrityViolationException alreadyStored) {
            log.debug("Exchange rate for {}/USD on {} was already stored", currency, day);
            close = exchangeRateRepository.findByBaseAndQuoteAndRateDate(currency, USD, day)
                .orElseThrow(RateNotFoundException::new)
                .getClose();
        }
        return remember(currency, day, close);
    }

    private BigDecimal remember(String currency, LocalDate day, BigDecimal close) {
        exchangeRateCache.put(currency, USD, day, close);
        return close;
    }
}
