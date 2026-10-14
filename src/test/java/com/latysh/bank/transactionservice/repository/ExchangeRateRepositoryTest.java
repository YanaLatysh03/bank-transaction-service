package com.latysh.bank.transactionservice.repository;

import com.latysh.bank.transactionservice.entity.ExchangeRate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class ExchangeRateRepositoryTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:15");

    @Autowired
    ExchangeRateRepository repository;

    @Test
    void findsRateForTheExactPairAndDate() {
        LocalDate date = LocalDate.parse("2026-03-13");

        ExchangeRate saved = repository.saveAndFlush(rate("EUR", "USD", date, "1.08500000"));

        Optional<ExchangeRate> found = repository.findByBaseAndQuoteAndRateDate("EUR", "USD", date);

        assertThat(found).isPresent();
        ExchangeRate actual = found.orElseThrow();
        assertThat(actual.getId()).isEqualTo(saved.getId());
        assertThat(actual.getBase()).isEqualTo("EUR");
        assertThat(actual.getQuote()).isEqualTo("USD");
        assertThat(actual.getRateDate()).isEqualTo(date);
        assertThat(actual.getClose()).isEqualByComparingTo("1.08500000");
    }

    @Test
    void returnsEmptyWhenDateDoesNotMatch() {
        LocalDate date = LocalDate.parse("2026-03-13");

        repository.saveAndFlush(rate("EUR", "USD", date, "1.08500000"));

        Optional<ExchangeRate> found = repository.findByBaseAndQuoteAndRateDate("EUR", "USD", date.minusDays(1));

        assertThat(found).isEmpty();
    }

    @Test
    void returnsEmptyWhenBaseDoesNotMatch() {
        LocalDate date = LocalDate.parse("2026-03-13");

        repository.saveAndFlush(rate("EUR", "USD", date, "1.08500000"));

        Optional<ExchangeRate> found = repository.findByBaseAndQuoteAndRateDate("KZT", "USD", date);

        assertThat(found).isEmpty();
    }

    @Test
    void returnsEmptyWhenQuoteDoesNotMatch() {
        LocalDate date = LocalDate.parse("2026-03-13");

        repository.saveAndFlush(rate("EUR", "USD", date, "1.08500000"));

        Optional<ExchangeRate> found = repository.findByBaseAndQuoteAndRateDate("EUR", "KZT", date);

        assertThat(found).isEmpty();
    }

    @Test
    void findsLatestRateStrictlyBeforeTheDate() {
        LocalDate older = LocalDate.parse("2026-03-11");
        LocalDate newer = LocalDate.parse("2026-03-12");
        LocalDate requested = LocalDate.parse("2026-03-13");

        repository.saveAndFlush(rate("EUR", "USD", older, "1.08000000"));
        ExchangeRate previousClose = repository.saveAndFlush(rate("EUR", "USD", newer, "1.09000000"));

        Optional<ExchangeRate> found = repository.findFirstByBaseAndQuoteAndRateDateLessThanOrderByRateDateDesc(
            "EUR", "USD", requested
        );

        assertThat(found).isPresent();
        ExchangeRate actual = found.orElseThrow();
        assertThat(actual.getId()).isEqualTo(previousClose.getId());
        assertThat(actual.getRateDate()).isEqualTo(newer);
        assertThat(actual.getClose()).isEqualByComparingTo("1.09000000");
    }

    @Test
    void returnsEmptyWhenEveryRateIsOnOrAfterTheDate() {
        LocalDate date = LocalDate.parse("2026-03-13");

        repository.saveAndFlush(rate("EUR", "USD", date, "1.08500000"));

        Optional<ExchangeRate> found = repository.findFirstByBaseAndQuoteAndRateDateLessThanOrderByRateDateDesc(
            "EUR", "USD", date.minusDays(1)
        );
        assertThat(found).isEmpty();
    }

    @Test
    void rejectsNonPositiveClose() {
        assertThatThrownBy(() -> repository.saveAndFlush(
            rate("EUR", "USD", LocalDate.parse("2026-03-13"), "0")
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDuplicatePairAndDate() {
        LocalDate date = LocalDate.parse("2026-03-13");
        repository.saveAndFlush(rate("EUR", "USD", date, "1.08500000"));
        assertThatThrownBy(() -> repository.saveAndFlush(rate("EUR", "USD", date, "1.09000000")))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static ExchangeRate rate(String base, String quote, LocalDate rateDate, String close) {
        return new ExchangeRate(base, quote, rateDate, new BigDecimal(close));
    }
}
