package com.latysh.bank.transactionservice.repository;

import com.latysh.bank.transactionservice.entity.ExpenseCategory;
import com.latysh.bank.transactionservice.entity.ExpenseLimit;
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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class ExpenseLimitRepositoryTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:15");

    @Autowired
    ExpenseLimitRepository repository;

    @Test
    void findsLimitWhenTransactionDatetimeEqualsLimitDatetime() {
        // given
        OffsetDateTime datetime = OffsetDateTime.parse("2026-03-01T12:00:00+03:00");
        ExpenseLimit saved = repository.saveAndFlush(limit("0000000123", ExpenseCategory.PRODUCT, "1500.00", datetime));

        // when
        Optional<ExpenseLimit> found = repository.findFirstByAccountFromAndExpenseCategoryAndLimitDatetimeLessThanEqualOrderByLimitDatetimeDesc(
            "0000000123", ExpenseCategory.PRODUCT, datetime
        );

        // then
        assertThat(found).isPresent();
        ExpenseLimit actual = found.orElseThrow();
        assertThat(actual.getId()).isEqualTo(saved.getId());
        assertThat(actual.getAccountFrom()).isEqualTo("0000000123");
        assertThat(actual.getExpenseCategory()).isEqualTo(ExpenseCategory.PRODUCT);
        assertThat(actual.getLimitSum()).isEqualByComparingTo("1500.00");
        assertThat(actual.getCurrencyShortname()).isEqualTo("USD");
        assertThat(actual.getLimitDatetime().toInstant()).isEqualTo(datetime.toInstant());
    }

    @Test
    void findsLimitWhenTransactionDatetimeAfterLimitDatetime() {
        // given
        OffsetDateTime limitDatetime = OffsetDateTime.parse("2026-03-01T00:00:00+03:00");
        ExpenseLimit saved = repository.saveAndFlush(limit("0000000123", ExpenseCategory.SERVICE, "800.00", limitDatetime));

        // when
        Optional<ExpenseLimit> found = repository.findFirstByAccountFromAndExpenseCategoryAndLimitDatetimeLessThanEqualOrderByLimitDatetimeDesc(
            "0000000123", ExpenseCategory.SERVICE, limitDatetime.plusDays(10)
        );

        // then
        assertThat(found).isPresent();
        ExpenseLimit actual = found.orElseThrow();
        assertThat(actual.getId()).isEqualTo(saved.getId());
    }

    @Test
    void returnsEmptyWhenTransactionDatetimeIsBeforeEveryLimit() {
        // given
        OffsetDateTime limitDatetime = OffsetDateTime.parse("2026-03-10T00:00:00+03:00");
        ExpenseLimit saved = repository.saveAndFlush(limit("0000000123", ExpenseCategory.SERVICE, "800.00", limitDatetime));

        // when
        Optional<ExpenseLimit> found = repository.findFirstByAccountFromAndExpenseCategoryAndLimitDatetimeLessThanEqualOrderByLimitDatetimeDesc(
            "0000000123", ExpenseCategory.SERVICE, limitDatetime.minusDays(10)
        );

        // then
        assertThat(found).isEmpty();
    }

    @Test
    void ignoresLimitSetAfterTheQueryDatetime() {
        // given
        OffsetDateTime limitDatetimeEarly = OffsetDateTime.parse("2026-03-01T00:00:00+03:00");
        OffsetDateTime limitDatetimeLater = OffsetDateTime.parse("2026-03-10T00:00:00+03:00");

        ExpenseLimit earlyLimit = repository.saveAndFlush(limit("0000000123", ExpenseCategory.SERVICE, "800.00", limitDatetimeEarly));
        ExpenseLimit lateLimit = repository.saveAndFlush(limit("0000000123", ExpenseCategory.SERVICE, "800.00", limitDatetimeLater));

        // when
        Optional<ExpenseLimit> found = repository.findFirstByAccountFromAndExpenseCategoryAndLimitDatetimeLessThanEqualOrderByLimitDatetimeDesc(
            "0000000123", ExpenseCategory.SERVICE, limitDatetimeEarly.plusDays(5)
        );

        // then
        assertThat(found).isPresent();
        ExpenseLimit actual = found.orElseThrow();
        assertThat(actual.getId()).isEqualTo(earlyLimit.getId());
    }

    @Test
    void doesNotFindLimitOfAnotherAccount() {
        // given
        OffsetDateTime datetime = OffsetDateTime.parse("2026-03-01T00:00:00+03:00");
        repository.saveAndFlush(limit("0000000123", ExpenseCategory.PRODUCT, "1000.00", datetime));

        // when
        Optional<ExpenseLimit> found = repository.findFirstByAccountFromAndExpenseCategoryAndLimitDatetimeLessThanEqualOrderByLimitDatetimeDesc(
            "0000000999", ExpenseCategory.PRODUCT, datetime
        );

        // then
        assertThat(found).isEmpty();
    }
    @Test
    void doesNotFindLimitOfAnotherCategory() {
        // given
        OffsetDateTime datetime = OffsetDateTime.parse("2026-03-01T00:00:00+03:00");
        repository.saveAndFlush(limit("0000000123", ExpenseCategory.PRODUCT, "1000.00", datetime));

        // when
        Optional<ExpenseLimit> found = repository.findFirstByAccountFromAndExpenseCategoryAndLimitDatetimeLessThanEqualOrderByLimitDatetimeDesc(
            "0000000123", ExpenseCategory.SERVICE, datetime
        );

        // then
        assertThat(found).isEmpty();
    }

    @Test
    void listsAllLimitsNewestFirst() {
        // given
        OffsetDateTime older = OffsetDateTime.parse("2026-01-01T00:00:00+03:00");
        OffsetDateTime middle = OffsetDateTime.parse("2026-02-01T00:00:00+03:00");
        OffsetDateTime newer = OffsetDateTime.parse("2026-03-01T00:00:00+03:00");

        repository.saveAndFlush(limit("0000000123", ExpenseCategory.PRODUCT, "1000.00", middle));
        repository.saveAndFlush(limit("0000000123", ExpenseCategory.SERVICE, "500.00", newer));
        repository.saveAndFlush(limit("0000000123", ExpenseCategory.PRODUCT, "250.00", older));

        // when
        List<ExpenseLimit> limits = repository.findAllByAccountFromOrderByLimitDatetimeDesc("0000000123");

        // then
        assertThat(limits)
            .extracting(limit -> limit.getLimitDatetime().toInstant())
            .containsExactly(newer.toInstant(), middle.toInstant(), older.toInstant());
    }

    @Test
    void rejectsNonPositiveLimitSum() {
        // when / then
        assertThatThrownBy(() -> repository.saveAndFlush(
            limit("0000000123", ExpenseCategory.PRODUCT, "0.00", OffsetDateTime.parse("2026-03-01T00:00:00+03:00"))
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    private static ExpenseLimit limit(String accountFrom, ExpenseCategory category, String sum, OffsetDateTime datetime) {
        return new ExpenseLimit(accountFrom, category, new BigDecimal(sum), datetime);
    }
}
