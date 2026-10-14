package com.latysh.bank.transactionservice.service.impl;

import com.latysh.bank.transactionservice.entity.BankTransaction;
import com.latysh.bank.transactionservice.entity.ExpenseCategory;
import com.latysh.bank.transactionservice.entity.ExpenseLimit;
import com.latysh.bank.transactionservice.repository.AccountCategoryLockRepository;
import com.latysh.bank.transactionservice.repository.BankTransactionRepository;
import com.latysh.bank.transactionservice.repository.ExpenseLimitRepository;
import com.latysh.bank.transactionservice.rq.IncomingTransactionRq;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class BankTransactionServiceTest {
    @Mock
    BankTransactionRepository bankTransactionRepository;

    @Mock
    ExpenseLimitRepository expenseLimitRepository;

    @Mock
    AccountCategoryLockRepository accountCategoryLockRepository;

    @InjectMocks
    BankTransactionService service;

    @Test
    void savesTransactionWithoutExceedingLimit() {
        // given
        OffsetDateTime datetime = OffsetDateTime.parse("2022-01-02T10:00:00+03:00");
        IncomingTransactionRq request = new IncomingTransactionRq(
            "0000000123",
            "9999999999",
            "USD",
            new BigDecimal("100.00"),
            ExpenseCategory.PRODUCT,
            datetime
        );
        ExpenseLimit limit = new ExpenseLimit(
            "0000000123",
            ExpenseCategory.PRODUCT,
            new BigDecimal("1000.00"),
            OffsetDateTime.parse("2022-01-01T00:00:00+03:00")
        );

        when(expenseLimitRepository.findFirstByAccountFromAndExpenseCategoryAndLimitDatetimeLessThanEqualOrderByLimitDatetimeDesc(
            "0000000123", ExpenseCategory.PRODUCT, datetime
        )).thenReturn(Optional.of(limit));

        ZoneId zone = ZoneId.of("Europe/Minsk");
        OffsetDateTime monthStart = LocalDate.of(2022, 1, 1).atStartOfDay(zone).toOffsetDateTime();

        when(bankTransactionRepository.sumUsd(
            "0000000123", ExpenseCategory.PRODUCT, monthStart, monthStart.plusMonths(1)
        )).thenReturn(BigDecimal.ZERO);

        BankTransaction persisted = mock(BankTransaction.class);
        when(persisted.getId()).thenReturn(7L);
        when(bankTransactionRepository.save(any())).thenReturn(persisted);

        // when
        long id = service.acceptTransaction(request, new BigDecimal("100.00"));

        // then
        assertThat(id).isEqualTo(7L);

        ArgumentCaptor<BankTransaction> saved = ArgumentCaptor.forClass(BankTransaction.class);
        verify(bankTransactionRepository).save(saved.capture());

        assertThat(saved.getValue().isLimitExceeded()).isFalse();
        assertThat(saved.getValue().getSumUsd()).isEqualByComparingTo("100.00");
        assertThat(saved.getValue().getExpenseLimit()).isSameAs(limit);

        verify(accountCategoryLockRepository).insertIfAbsent("0000000123", "product");
        verify(accountCategoryLockRepository).lock(eq("0000000123"), eq(ExpenseCategory.PRODUCT));
    }

    @Test
    void savesTransactionExceedingLimit() {
        // given
        OffsetDateTime datetime = OffsetDateTime.parse("2022-01-02T10:00:00+03:00");
        IncomingTransactionRq request = new IncomingTransactionRq(
            "0000000123",
            "9999999999",
            "USD",
            new BigDecimal("800.00"),
            ExpenseCategory.PRODUCT,
            datetime
        );
        ExpenseLimit limit = new ExpenseLimit(
            "0000000123",
            ExpenseCategory.PRODUCT,
            new BigDecimal("1000.00"),
            OffsetDateTime.parse("2022-01-01T00:00:00+03:00")
        );

        when(expenseLimitRepository.findFirstByAccountFromAndExpenseCategoryAndLimitDatetimeLessThanEqualOrderByLimitDatetimeDesc(
            "0000000123", ExpenseCategory.PRODUCT, datetime
        )).thenReturn(Optional.of(limit));

        ZoneId zone = ZoneId.of("Europe/Minsk");
        OffsetDateTime monthStart = LocalDate.of(2022, 1, 1).atStartOfDay(zone).toOffsetDateTime();
        when(bankTransactionRepository.sumUsd(
            "0000000123", ExpenseCategory.PRODUCT, monthStart, monthStart.plusMonths(1)
        )).thenReturn(BigDecimal.valueOf(300));

        BankTransaction persisted = mock(BankTransaction.class);
        when(persisted.getId()).thenReturn(7L);
        when(bankTransactionRepository.save(any())).thenReturn(persisted);

        // when
        long id = service.acceptTransaction(request, new BigDecimal("800.00"));

        // then
        assertThat(id).isEqualTo(7L);

        ArgumentCaptor<BankTransaction> saved = ArgumentCaptor.forClass(BankTransaction.class);
        verify(bankTransactionRepository).save(saved.capture());

        assertThat(saved.getValue().isLimitExceeded()).isTrue();
        assertThat(saved.getValue().getSumUsd()).isEqualByComparingTo("800.00");
        assertThat(saved.getValue().getExpenseLimit()).isSameAs(limit);
    }

    @Test
    void savesTransactionWithoutExceedingLimitWhenTotalSumEqualsToLimit() {
        // given
        OffsetDateTime datetime = OffsetDateTime.parse("2022-01-13T10:00:00+03:00");
        IncomingTransactionRq request = new IncomingTransactionRq(
            "0000000123",
            "9999999999",
            "USD",
            new BigDecimal("100.00"),
            ExpenseCategory.PRODUCT,
            datetime
        );
        ExpenseLimit limit = new ExpenseLimit(
            "0000000123",
            ExpenseCategory.PRODUCT,
            new BigDecimal("1000.00"),
            OffsetDateTime.parse("2022-01-01T00:00:00+03:00")
        );

        when(expenseLimitRepository.findFirstByAccountFromAndExpenseCategoryAndLimitDatetimeLessThanEqualOrderByLimitDatetimeDesc(
            "0000000123", ExpenseCategory.PRODUCT, datetime
        )).thenReturn(Optional.of(limit));

        ZoneId zone = ZoneId.of("Europe/Minsk");
        OffsetDateTime monthStart = LocalDate.of(2022, 1, 1).atStartOfDay(zone).toOffsetDateTime();

        when(bankTransactionRepository.sumUsd(
            "0000000123", ExpenseCategory.PRODUCT, monthStart, monthStart.plusMonths(1)
        )).thenReturn(new BigDecimal("900.00"));

        BankTransaction persisted = mock(BankTransaction.class);
        when(persisted.getId()).thenReturn(8L);
        when(bankTransactionRepository.save(any())).thenReturn(persisted);

        // when
        long id = service.acceptTransaction(request, new BigDecimal("100.00"));

        // then
        assertThat(id).isEqualTo(8L);
        ArgumentCaptor<BankTransaction> saved = ArgumentCaptor.forClass(BankTransaction.class);
        verify(bankTransactionRepository).save(saved.capture());
        assertThat(saved.getValue().isLimitExceeded()).isFalse();
        assertThat(saved.getValue().getExpenseLimit()).isSameAs(limit);
    }

    @Test
    void savesTransactionWithoutLimit() {
        // given
        OffsetDateTime datetime = OffsetDateTime.parse("2022-01-02T10:00:00+03:00");
        IncomingTransactionRq request = new IncomingTransactionRq(
            "0000000123",
            "9999999999",
            "USD",
            new BigDecimal("100.00"),
            ExpenseCategory.PRODUCT,
            datetime
        );

        when(expenseLimitRepository.findFirstByAccountFromAndExpenseCategoryAndLimitDatetimeLessThanEqualOrderByLimitDatetimeDesc(
            "0000000123", ExpenseCategory.PRODUCT, datetime
        )).thenReturn(Optional.empty());

        ZoneId zone = ZoneId.of("Europe/Minsk");
        OffsetDateTime monthStart = LocalDate.of(2022, 1, 1).atStartOfDay(zone).toOffsetDateTime();
        when(bankTransactionRepository.sumUsd(
            "0000000123", ExpenseCategory.PRODUCT, monthStart, monthStart.plusMonths(1)
        )).thenReturn(BigDecimal.ZERO);
        BankTransaction persisted = mock(BankTransaction.class);
        when(persisted.getId()).thenReturn(9L);
        when(bankTransactionRepository.save(any())).thenReturn(persisted);
        // when
        long id = service.acceptTransaction(request, new BigDecimal("100.00"));
        // then
        assertThat(id).isEqualTo(9L);
        ArgumentCaptor<BankTransaction> saved = ArgumentCaptor.forClass(BankTransaction.class);
        verify(bankTransactionRepository).save(saved.capture());
        assertThat(saved.getValue().isLimitExceeded()).isFalse();
        assertThat(saved.getValue().getExpenseLimit()).isNull();
    }

    @Test
    void savesTransactionWhenSumAsZero() {
        // given
        OffsetDateTime datetime = OffsetDateTime.parse("2022-01-02T10:00:00+03:00");
        IncomingTransactionRq request = new IncomingTransactionRq(
            "0000000123",
            "9999999999",
            "USD",
            new BigDecimal("100.00"),
            ExpenseCategory.PRODUCT,
            datetime
        );
        ExpenseLimit limit = new ExpenseLimit(
            "0000000123",
            ExpenseCategory.PRODUCT,
            new BigDecimal("1000.00"),
            OffsetDateTime.parse("2022-01-01T00:00:00+03:00")
        );

        when(expenseLimitRepository.findFirstByAccountFromAndExpenseCategoryAndLimitDatetimeLessThanEqualOrderByLimitDatetimeDesc(
            "0000000123", ExpenseCategory.PRODUCT, datetime
        )).thenReturn(Optional.of(limit));
        ZoneId zone = ZoneId.of("Europe/Minsk");
        OffsetDateTime monthStart = LocalDate.of(2022, 1, 1).atStartOfDay(zone).toOffsetDateTime();

        when(bankTransactionRepository.sumUsd(
            "0000000123", ExpenseCategory.PRODUCT, monthStart, monthStart.plusMonths(1)
        )).thenReturn(null);

        BankTransaction persisted = mock(BankTransaction.class);
        when(persisted.getId()).thenReturn(10L);
        when(bankTransactionRepository.save(any())).thenReturn(persisted);

        // when
        long id = service.acceptTransaction(request, new BigDecimal("100.00"));

        // then
        assertThat(id).isEqualTo(10L);

        ArgumentCaptor<BankTransaction> saved = ArgumentCaptor.forClass(BankTransaction.class);
        verify(bankTransactionRepository).save(saved.capture());
        assertThat(saved.getValue().isLimitExceeded()).isFalse();
    }

    @Test
    void savesTransactionWithoutExceedingLimitInNewMonth() {
        // given
        OffsetDateTime datetime = OffsetDateTime.parse("2022-02-03T10:00:00+03:00");
        IncomingTransactionRq request = new IncomingTransactionRq(
            "0000000123",
            "9999999999",
            "USD",
            new BigDecimal("100.00"),
            ExpenseCategory.PRODUCT,
            datetime
        );
        ExpenseLimit limit = new ExpenseLimit(
            "0000000123",
            ExpenseCategory.PRODUCT,
            new BigDecimal("1000.00"),
            OffsetDateTime.parse("2022-01-01T00:00:00+03:00")
        );

        when(expenseLimitRepository.findFirstByAccountFromAndExpenseCategoryAndLimitDatetimeLessThanEqualOrderByLimitDatetimeDesc(
            "0000000123", ExpenseCategory.PRODUCT, datetime
        )).thenReturn(Optional.of(limit));

        ZoneId zone = ZoneId.of("Europe/Minsk");
        OffsetDateTime monthStart = LocalDate.of(2022, 2, 1).atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime monthEnd = monthStart.plusMonths(1);

        when(bankTransactionRepository.sumUsd(
            "0000000123", ExpenseCategory.PRODUCT, monthStart, monthEnd
        )).thenReturn(BigDecimal.ZERO);

        BankTransaction persisted = mock(BankTransaction.class);
        when(persisted.getId()).thenReturn(11L);
        when(bankTransactionRepository.save(any())).thenReturn(persisted);

        // when
        long id = service.acceptTransaction(request, new BigDecimal("100.00"));

        // then
        assertThat(id).isEqualTo(11L);

        ArgumentCaptor<BankTransaction> saved = ArgumentCaptor.forClass(BankTransaction.class);
        verify(bankTransactionRepository).save(saved.capture());
        assertThat(saved.getValue().isLimitExceeded()).isFalse();
        verify(bankTransactionRepository).sumUsd(
            "0000000123", ExpenseCategory.PRODUCT, monthStart, monthEnd
        );
    }
}
