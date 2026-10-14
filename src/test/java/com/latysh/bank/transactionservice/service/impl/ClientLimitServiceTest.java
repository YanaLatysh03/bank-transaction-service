package com.latysh.bank.transactionservice.service.impl;

import com.latysh.bank.transactionservice.entity.BankTransaction;
import com.latysh.bank.transactionservice.entity.ExpenseCategory;
import com.latysh.bank.transactionservice.entity.ExpenseLimit;
import com.latysh.bank.transactionservice.repository.BankTransactionRepository;
import com.latysh.bank.transactionservice.repository.ExpenseLimitRepository;
import com.latysh.bank.transactionservice.rq.CreateLimitRq;
import com.latysh.bank.transactionservice.rq.GetClientDataRq;
import com.latysh.bank.transactionservice.rs.ExceededTransactionRs;
import com.latysh.bank.transactionservice.rs.LimitRs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClientLimitServiceTest {
    @Mock
    ExpenseLimitRepository expenseLimitRepository;

    @Mock
    BankTransactionRepository bankTransactionRepository;

    ClientLimitServiceImpl service;

    Clock clock = Clock.system(
        ZoneId.of("Europe/Minsk")
    );

    @BeforeEach
    void setUp() {
        service = new ClientLimitServiceImpl(
            expenseLimitRepository,
            bankTransactionRepository,
            clock
        );
    }

    @Test
    void savesLimit() {
        // given
        CreateLimitRq request = new CreateLimitRq(
            "0000000123",
            ExpenseCategory.PRODUCT,
            new BigDecimal("1000.00")
        );
        OffsetDateTime storedDatetime = OffsetDateTime.parse("2022-01-10T12:00:00+03:00");

        ExpenseLimit persisted = mock(ExpenseLimit.class);
        when(persisted.getId()).thenReturn(5L);
        when(persisted.getAccountFrom()).thenReturn("0000000123");
        when(persisted.getExpenseCategory()).thenReturn(ExpenseCategory.PRODUCT);
        when(persisted.getLimitSum()).thenReturn(new BigDecimal("1000.00"));
        when(persisted.getLimitDatetime()).thenReturn(storedDatetime);
        when(persisted.getCurrencyShortname()).thenReturn("USD");

        when(expenseLimitRepository.saveAndFlush(any())).thenReturn(persisted);

        // when
        LimitRs response = service.createLimit(request);

        // then
        assertThat(response).isEqualTo(new LimitRs(
            5L,
            "0000000123",
            ExpenseCategory.PRODUCT,
            new BigDecimal("1000.00"),
            storedDatetime,
            "USD"
        ));
        ArgumentCaptor<ExpenseLimit> saved = ArgumentCaptor.forClass(ExpenseLimit.class);
        verify(expenseLimitRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getAccountFrom()).isEqualTo("0000000123");
        assertThat(saved.getValue().getExpenseCategory()).isEqualTo(ExpenseCategory.PRODUCT);
        assertThat(saved.getValue().getLimitSum()).isEqualByComparingTo("1000.00");
        assertThat(saved.getValue().getCurrencyShortname()).isEqualTo("USD");

        verifyNoInteractions(bankTransactionRepository);
    }

    @Test
    void returnsLimits() {
        // given
        OffsetDateTime newerDatetime = OffsetDateTime.parse("2022-01-10T00:00:00+03:00");
        OffsetDateTime olderDatetime = OffsetDateTime.parse("2022-01-01T00:00:00+03:00");

        ExpenseLimit newer = spy(new ExpenseLimit(
            "0000000123", ExpenseCategory.PRODUCT, new BigDecimal("2000.00"), newerDatetime
        ));
        when(newer.getId()).thenReturn(2L);

        ExpenseLimit older = spy(new ExpenseLimit(
            "0000000123", ExpenseCategory.SERVICE, new BigDecimal("1000.00"), olderDatetime
        ));
        when(older.getId()).thenReturn(1L);

        when(expenseLimitRepository.findAllByAccountFromOrderByLimitDatetimeDesc(newer.getAccountFrom()))
            .thenReturn(List.of(newer, older));
        // when
        List<LimitRs> response = service.findAllLimitsByAccountFrom(newer.getAccountFrom());
        // then
        assertThat(response).isEqualTo(List.of(
            new LimitRs(2L, "0000000123", ExpenseCategory.PRODUCT, new BigDecimal("2000.00"), newerDatetime, "USD"),
            new LimitRs(1L, "0000000123", ExpenseCategory.SERVICE, new BigDecimal("1000.00"), olderDatetime, "USD")
        ));
        verifyNoInteractions(bankTransactionRepository);
    }

    @Test
    void returnsExceededTransactionWithLimit() {
        // given
        OffsetDateTime limitDatetime = OffsetDateTime.parse("2022-01-01T00:00:00+03:00");
        OffsetDateTime datetime = OffsetDateTime.parse("2022-01-03T10:15:00+03:00");
        ExpenseLimit limit = new ExpenseLimit(
            "0000000123", ExpenseCategory.PRODUCT, new BigDecimal("500.00"), limitDatetime
        );

        BankTransaction transaction = new BankTransaction(
            "0000000123",
            "9999999999",
            "KZT",
            new BigDecimal("10000.45"),
            ExpenseCategory.PRODUCT,
            datetime,
            new BigDecimal("23.00"),
            true,
            limit
        );
        when(bankTransactionRepository.findExceededTransactionsByAccountFrom(transaction.getAccountFrom())).thenReturn(List.of(transaction));

        // when
        List<ExceededTransactionRs> response = service.findLimitExceededTransactionsByAccountFrom(transaction.getAccountFrom());
        // then
        assertThat(response).isEqualTo(List.of(new ExceededTransactionRs(
            "0000000123",
            "9999999999",
            "KZT",
            new BigDecimal("10000.45"),
            ExpenseCategory.PRODUCT,
            datetime,
            new BigDecimal("500.00"),
            limitDatetime,
            "USD"
        )));
        verifyNoInteractions(expenseLimitRepository);
    }
    @Test
    void returnsExceededTransactionWithoutLimit() {
        // given
        OffsetDateTime datetime = OffsetDateTime.parse("2022-01-03T10:15:00+03:00");
        BankTransaction transaction = new BankTransaction(
            "0000000123",
            "9999999999",
            "KZT",
            new BigDecimal("10000.45"),
            ExpenseCategory.PRODUCT,
            datetime,
            new BigDecimal("23.00"),
            true,
            null
        );

        when(bankTransactionRepository.findExceededTransactionsByAccountFrom(transaction.getAccountFrom())).thenReturn(List.of(transaction));
        // when
        List<ExceededTransactionRs> response = service.findLimitExceededTransactionsByAccountFrom(transaction.getAccountFrom());
        // then
        assertThat(response).isEqualTo(List.of(new ExceededTransactionRs(
            "0000000123",
            "9999999999",
            "KZT",
            new BigDecimal("10000.45"),
            ExpenseCategory.PRODUCT,
            datetime,
            new BigDecimal("1000.00"),
            null,
            "USD"
        )));
    }
}
