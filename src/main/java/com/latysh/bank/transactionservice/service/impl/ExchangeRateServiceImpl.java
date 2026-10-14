package com.latysh.bank.transactionservice.service.impl;

import com.latysh.bank.transactionservice.DomainConstants;
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
    private final ExchangeRateRepository exchangeRateRepository;
    private final ExchangeRateClient client;
    private final ExchangeRateCache exchangeRateCache;

    @Override
    public BigDecimal toUsd(String currency, BigDecimal sum, LocalDate day) {
        return sum.multiply(usdPerUnit(currency, day)).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Возвращает курс USD за единицу валюты на день.
     * Для USD это {@code 1}, без обращения к кэшу, базе и провайдеру.
     */
    private BigDecimal usdPerUnit(String currency, LocalDate day) {
        if (DomainConstants.USD.equals(currency)) {
            return BigDecimal.ONE;
        }
        return exchangeRateCache.get(currency, DomainConstants.USD, day)
            .orElseGet(() -> loadUsdPerUnit(currency, day));
    }

    /**
     * Ищет курс дня вне кэша: сначала строка в базе, затем провайдер.
     * Если провайдер не отдал курс, берёт последнее закрытие строго раньше дня и в кэш его не пишет.
     *
     * @throws RateNotFoundException если нет ни курса дня, ни прошлого закрытия
     */
    private BigDecimal loadUsdPerUnit(String currency, LocalDate day) {
        return exchangeRateRepository.findByBaseAndQuoteAndRateDate(currency, DomainConstants.USD, day)
            .map(rate -> cacheClose(currency, day, rate.getClose()))
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
                        .findFirstByBaseAndQuoteAndRateDateLessThanOrderByRateDateDesc(currency, DomainConstants.USD, day)
                        .map(ExchangeRate::getClose)
                        .orElseThrow(RateNotFoundException::new);
                }
            });
    }

    /**
     * Запрашивает курс у провайдера и сохраняет его в базу.
     * Если строка за этот день уже записана параллельным запросом, читает её.
     *
     * @throws ExchangeRateUnavailableException если провайдер не вернул положительный курс
     * @throws RateNotFoundException если после такой гонки строка в базе не найдена
     */
    private BigDecimal fetchAndStore(String currency, LocalDate day) throws ExchangeRateUnavailableException {
        log.info("Fetching exchange rate for {}/{} on {}", currency, DomainConstants.USD, day);
        BigDecimal close = client.getRate(currency, DomainConstants.USD, day)
            .orElseThrow(() -> new ExchangeRateUnavailableException(currency, day));
        try {
            exchangeRateRepository.saveAndFlush(new ExchangeRate(currency, DomainConstants.USD, day, close));
        } catch (DataIntegrityViolationException alreadyStored) {
            log.debug("Exchange rate for {}/{} on {} was already stored", currency, DomainConstants.USD, day);
            close = exchangeRateRepository.findByBaseAndQuoteAndRateDate(currency, DomainConstants.USD, day)
                .orElseThrow(RateNotFoundException::new)
                .getClose();
        }
        return cacheClose(currency, day, close);
    }

    /**
     * Кладёт курс запрошенного дня в кэш и возвращает его.
     */
    private BigDecimal cacheClose(String currency, LocalDate day, BigDecimal close) {
        exchangeRateCache.put(currency, DomainConstants.USD, day, close);
        return close;
    }
}
