package com.latysh.bank.transactionservice.repository;

import com.latysh.bank.transactionservice.entity.BankTransaction;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class BankTransactionRepositoryTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:15");

    @Autowired
    BankTransactionRepository bankTransactionRepository;

    @Autowired
    ExpenseLimitRepository expenseLimitRepository;

    @Test
    void sumsUsdInsideTheMonthInterval() {
        // given
        OffsetDateTime monthStart = OffsetDateTime.parse("2026-03-01T00:00:00+03:00");
        OffsetDateTime monthEnd = OffsetDateTime.parse("2026-04-01T00:00:00+03:00");

        bankTransactionRepository.saveAndFlush(transaction("0000000123", ExpenseCategory.PRODUCT, "100.50", monthStart.plusDays(5), false, null));
        bankTransactionRepository.saveAndFlush(transaction("0000000123", ExpenseCategory.PRODUCT, "40.25", monthStart.plusDays(20), true, null));

        // when
        BigDecimal total = bankTransactionRepository.sumUsd("0000000123", ExpenseCategory.PRODUCT, monthStart, monthEnd);

        // then
        assertThat(total).isEqualByComparingTo("140.75");
    }

    @Test
    void includesTransactionAtMonthStart() {
        // given
        OffsetDateTime monthStart = OffsetDateTime.parse("2026-03-01T00:00:00+03:00");
        OffsetDateTime monthEnd = OffsetDateTime.parse("2026-04-01T00:00:00+03:00");

        bankTransactionRepository.saveAndFlush(transaction("0000000123", ExpenseCategory.PRODUCT, "15.00", monthStart, false, null));

        // when
        BigDecimal total = bankTransactionRepository.sumUsd("0000000123", ExpenseCategory.PRODUCT, monthStart, monthEnd);

        // then
        assertThat(total).isEqualByComparingTo("15.00");
    }

    @Test
    void excludesTransactionAtMonthEnd() {
        // given
        OffsetDateTime monthStart = OffsetDateTime.parse("2026-03-01T00:00:00+03:00");
        OffsetDateTime monthEnd = OffsetDateTime.parse("2026-04-01T00:00:00+03:00");

        bankTransactionRepository.saveAndFlush(transaction("0000000123", ExpenseCategory.PRODUCT, "15.00", monthEnd, false, null));

        // when
        BigDecimal total = bankTransactionRepository.sumUsd("0000000123", ExpenseCategory.PRODUCT, monthStart, monthEnd);

        // then
        assertThat(total).isNull();
    }

    @Test
    void doesNotSumAnotherAccount() {
        // given
        OffsetDateTime monthStart = OffsetDateTime.parse("2026-03-01T00:00:00+03:00");
        OffsetDateTime monthEnd = OffsetDateTime.parse("2026-04-01T00:00:00+03:00");

        bankTransactionRepository.saveAndFlush(transaction("0000000123", ExpenseCategory.PRODUCT, "15.00", monthStart.plusDays(1), false, null));

        // when
        BigDecimal total = bankTransactionRepository.sumUsd("0000000999", ExpenseCategory.PRODUCT, monthStart, monthEnd);

        // then
        assertThat(total).isNull();
    }

    @Test
    void doesNotSumAnotherCategory() {
        // given
        OffsetDateTime monthStart = OffsetDateTime.parse("2026-03-01T00:00:00+03:00");
        OffsetDateTime monthEnd = OffsetDateTime.parse("2026-04-01T00:00:00+03:00");

        bankTransactionRepository.saveAndFlush(transaction("0000000123", ExpenseCategory.PRODUCT, "15.00", monthStart.plusDays(1), false, null));

        // when
        BigDecimal total = bankTransactionRepository.sumUsd("0000000123", ExpenseCategory.SERVICE, monthStart, monthEnd);

        // then
        assertThat(total).isNull();
    }

    @Test
    void returnsExceededTransactionsOrderedByDatetime() {
        // given
        OffsetDateTime earlier = OffsetDateTime.parse("2026-03-01T00:00:00+03:00");
        OffsetDateTime later = OffsetDateTime.parse("2026-03-20T00:00:00+03:00");

        ExpenseLimit limit = expenseLimitRepository.saveAndFlush(
            new ExpenseLimit("0000000123", ExpenseCategory.PRODUCT, new BigDecimal("1000.00"), earlier)
        );
        BankTransaction laterTransaction = bankTransactionRepository.saveAndFlush(
            transaction("0000000123", ExpenseCategory.PRODUCT, "700.00", later, true, limit)
        );
        BankTransaction earlierTransaction = bankTransactionRepository.saveAndFlush(
            transaction("0000000123", ExpenseCategory.SERVICE, "50.00", earlier, true, null)
        );
        bankTransactionRepository.saveAndFlush(transaction("0000000123", ExpenseCategory.PRODUCT, "10.00", earlier.plusDays(2), false, limit));

        // when
        List<BankTransaction> exceeded = bankTransactionRepository.findExceededTransactionsByAccountFrom("0000000123");

        // then
        assertThat(exceeded)
            .extracting(BankTransaction::getId)
            .containsExactly(earlierTransaction.getId(), laterTransaction.getId());
        assertThat(exceeded.get(1).getExpenseLimit().getId()).isEqualTo(limit.getId());
        assertThat(exceeded.get(0).getExpenseLimit()).isNull();
    }
    @Test
    void rejectsNegativeSum() {
        // when / then
        assertThatThrownBy(() -> bankTransactionRepository.saveAndFlush(
            transaction("0000000123", ExpenseCategory.PRODUCT, "-1.00", OffsetDateTime.parse("2026-03-01T00:00:00+03:00"), false, null)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    private static BankTransaction transaction(String accountFrom, ExpenseCategory category, String sum,
                                               OffsetDateTime datetime, boolean limitExceeded, ExpenseLimit expenseLimit) {
        BigDecimal amount = new BigDecimal(sum);
        return new BankTransaction(
            accountFrom, "0000000999", "USD", amount, category, datetime, amount, limitExceeded, expenseLimit
        );
    }
}
