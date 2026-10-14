package com.latysh.bank.transactionservice.service.impl;

import com.latysh.bank.transactionservice.cache.ExchangeRateCache;
import com.latysh.bank.transactionservice.entity.ExchangeRate;
import com.latysh.bank.transactionservice.exception.RateNotFoundException;
import com.latysh.bank.transactionservice.rates.ExchangeRateClient;
import com.latysh.bank.transactionservice.repository.ExchangeRateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ExchangeRateServiceTest {
    @Mock
    ExchangeRateRepository exchangeRateRepository;

    @Mock
    ExchangeRateClient client;

    @Mock
    ExchangeRateCache exchangeRateCache;

    @InjectMocks
    ExchangeRateServiceImpl service;

    @BeforeEach
    void cacheMiss() {
        lenient().when(exchangeRateCache.get(any(), any(), any())).thenReturn(Optional.empty());
    }

    @Test
    void convertsSumForUsd() {
        // given
        LocalDate day = LocalDate.of(2022, 1, 2);

        // when
        BigDecimal sumUsd = service.toUsd("USD", new BigDecimal("100.00"), day);

        // then
        assertThat(sumUsd).isEqualByComparingTo("100.00");
        verifyNoInteractions(exchangeRateRepository, client, exchangeRateCache);
    }

    @Test
    void convertsSumByCachedRate() {
        // given
        LocalDate day = LocalDate.of(2022, 1, 2);
        when(exchangeRateCache.get("KZT", "USD", day))
            .thenReturn(Optional.of(new BigDecimal("0.00230000")));

        // when
        BigDecimal sumUsd = service.toUsd("KZT", new BigDecimal("10000.00"), day);

        // then
        assertThat(sumUsd).isEqualByComparingTo("23.00");
        verifyNoInteractions(exchangeRateRepository, client);
        verify(exchangeRateCache, never()).put(any(), any(), any(), any());
    }

    @Test
    void convertsSumByStoredRate() {
        // given
        LocalDate day = LocalDate.of(2022, 1, 2);
        ExchangeRate rate = new ExchangeRate("KZT", "USD", day, new BigDecimal("0.00230000"));

        when(exchangeRateRepository.findByBaseAndQuoteAndRateDate("KZT", "USD", day))
            .thenReturn(Optional.of(rate));

        // when
        BigDecimal sumUsd = service.toUsd("KZT", new BigDecimal("10000.00"), day);

        // then
        assertThat(sumUsd).isEqualByComparingTo("23.00");
        verifyNoInteractions(client);
        verify(exchangeRateRepository, never()).saveAndFlush(any());
        verify(exchangeRateCache).put("KZT", "USD", day, rate.getClose());
    }

    @Test
    void convertsSumByFetchedRate() {
        // given
        LocalDate day = LocalDate.of(2022, 1, 2);
        when(exchangeRateRepository.findByBaseAndQuoteAndRateDate("KZT", "USD", day))
            .thenReturn(Optional.empty());
        when(client.getExchangeRates("KZT", "USD", day))
            .thenReturn(Optional.of(new BigDecimal("0.00230000")));
        // when
        BigDecimal sumUsd = service.toUsd("KZT", new BigDecimal("10000.00"), day);
        // then
        assertThat(sumUsd).isEqualByComparingTo("23.00");
        ArgumentCaptor<ExchangeRate> stored = ArgumentCaptor.forClass(ExchangeRate.class);
        verify(exchangeRateRepository).saveAndFlush(stored.capture());
        assertThat(stored.getValue().getBase()).isEqualTo("KZT");
        assertThat(stored.getValue().getQuote()).isEqualTo("USD");
        assertThat(stored.getValue().getRateDate()).isEqualTo(day);
        assertThat(stored.getValue().getClose()).isEqualByComparingTo("0.00230000");
        verify(exchangeRateCache).put("KZT", "USD", day, new BigDecimal("0.00230000"));
    }

    @Test
    void convertsSumByPreviousClose() {
        // given
        LocalDate day = LocalDate.of(2022, 1, 2);
        ExchangeRate previous = new ExchangeRate(
            "KZT", "USD", LocalDate.of(2021, 12, 31), new BigDecimal("0.00210000")
        );
        when(exchangeRateRepository.findByBaseAndQuoteAndRateDate("KZT", "USD", day))
            .thenReturn(Optional.empty());
        when(client.getExchangeRates("KZT", "USD", day))
            .thenReturn(Optional.empty());
        when(exchangeRateRepository.findFirstByBaseAndQuoteAndRateDateLessThanOrderByRateDateDesc("KZT", "USD", day))
            .thenReturn(Optional.of(previous));

        // when
        BigDecimal sumUsd = service.toUsd("KZT", new BigDecimal("10000.00"), day);

        // then
        assertThat(sumUsd).isEqualByComparingTo("21.00");
        verify(exchangeRateRepository, never()).saveAndFlush(any());
        verify(exchangeRateCache, never()).put(any(), any(), any(), any());
    }

    @Test
    void throwsRateNotFoundExceptionWhenNoCloseExists() {
        // given
        LocalDate day = LocalDate.of(2022, 1, 2);
        when(exchangeRateRepository.findByBaseAndQuoteAndRateDate("KZT", "USD", day))
            .thenReturn(Optional.empty());
        when(client.getExchangeRates("KZT", "USD", day))
            .thenReturn(Optional.empty());
        when(exchangeRateRepository.findFirstByBaseAndQuoteAndRateDateLessThanOrderByRateDateDesc("KZT", "USD", day))
            .thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> service.toUsd("KZT", new BigDecimal("10000.00"), day))
            .isInstanceOf(RateNotFoundException.class);
        verify(exchangeRateRepository, never()).saveAndFlush(any());
        verify(exchangeRateCache, never()).put(any(), any(), any(), any());
    }

    @Test
    void convertsSumWhenRateIsAlreadyStored() {
        // given
        LocalDate day = LocalDate.of(2022, 1, 2);
        ExchangeRate stored = new ExchangeRate("KZT", "USD", day, new BigDecimal("0.00250000"));
        when(exchangeRateRepository.findByBaseAndQuoteAndRateDate("KZT", "USD", day))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(stored));
        when(client.getExchangeRates("KZT", "USD", day))
            .thenReturn(Optional.of(new BigDecimal("0.00230000")));
        when(exchangeRateRepository.saveAndFlush(any()))
            .thenThrow(new DataIntegrityViolationException("duplicate"));

        // when
        BigDecimal sumUsd = service.toUsd("KZT", new BigDecimal("10000.00"), day);

        // then
        assertThat(sumUsd).isEqualByComparingTo("25.00");
        verify(exchangeRateCache).put("KZT", "USD", day, stored.getClose());
    }

    @Test
    void throwsRateNotFoundExceptionWhenStoredRateIsMissing() {
        // given
        LocalDate day = LocalDate.of(2022, 1, 2);
        when(exchangeRateRepository.findByBaseAndQuoteAndRateDate("KZT", "USD", day))
            .thenReturn(Optional.empty());
        when(client.getExchangeRates("KZT", "USD", day))
            .thenReturn(Optional.of(new BigDecimal("0.00230000")));
        when(exchangeRateRepository.saveAndFlush(any()))
            .thenThrow(new DataIntegrityViolationException("duplicate"));

        // when / then
        assertThatThrownBy(() -> service.toUsd("KZT", new BigDecimal("10000.00"), day))
            .isInstanceOf(RateNotFoundException.class);
        verify(exchangeRateCache, never()).put(any(), any(), any(), any());
    }

}
