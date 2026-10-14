package com.latysh.bank.transactionservice.service.impl;

import com.latysh.bank.transactionservice.DomainConstants;
import com.latysh.bank.transactionservice.entity.BankTransaction;
import com.latysh.bank.transactionservice.entity.ExpenseCategory;
import com.latysh.bank.transactionservice.repository.AccountCategoryLockRepository;
import com.latysh.bank.transactionservice.repository.BankTransactionRepository;
import com.latysh.bank.transactionservice.repository.ExpenseLimitRepository;
import com.latysh.bank.transactionservice.rq.IncomingTransactionRq;
import com.latysh.bank.transactionservice.service.BankTransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class BankTransactionServiceImpl implements BankTransactionService {
    private final BankTransactionRepository bankTransactionRepository;
    private final ExpenseLimitRepository expenseLimitRepository;
    private final AccountCategoryLockRepository accountCategoryLockRepository;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    @Override
    public long acceptTransaction(IncomingTransactionRq request, BigDecimal sumUsd) {
        ExpenseCategory category = request.expenseCategory();

        accountCategoryLockRepository.insertIfAbsent(request.accountFrom(), category.name().toLowerCase());
        accountCategoryLockRepository.lock(request.accountFrom(), category);

        // Нахождение лимита по accountFrom, категории и дате,
        // которая меньше ли бо равна дате транзакции
        var limit = expenseLimitRepository.findFirstByAccountFromAndExpenseCategoryAndLimitDatetimeLessThanEqualOrderByLimitDatetimeDesc(
            request.accountFrom(), category, request.datetime()
        ).orElse(null);

        BigDecimal limitSum = limit == null ? DomainConstants.DEFAULT_LIMIT : limit.getLimitSum();
        if (limit == null) {
            log.debug("No expense limit for account {} category {}, using default {}",
                request.accountFrom(), category, DomainConstants.DEFAULT_LIMIT);
        }

        ZonedDateTime zoned = request.datetime().atZoneSameInstant(DomainConstants.BUSINESS_ZONE);

        OffsetDateTime monthStart = zoned.withDayOfMonth(1).toLocalDate()
            .atStartOfDay(DomainConstants.BUSINESS_ZONE)
            .toOffsetDateTime();

        OffsetDateTime monthEnd = monthStart.plusMonths(1);

        BigDecimal totalSum = bankTransactionRepository.sumUsd(
            request.accountFrom(), category, monthStart, monthEnd);

        if (totalSum == null) totalSum = BigDecimal.ZERO;

        boolean limitExceeded = limitSum.subtract(totalSum)
            .subtract(sumUsd).compareTo(BigDecimal.ZERO) < 0;

        BankTransaction saved = bankTransactionRepository.save(new BankTransaction(
            request.accountFrom(),
            request.accountTo(),
            request.currencyShortname(),
            request.sum(),
            category,
            request.datetime(),
            sumUsd,
            limitExceeded,
            limit
        ));

        log.info("Stored transaction {} for account {} category {}, limit exceeded={}",
            saved.getId(), request.accountFrom(), category, limitExceeded);
        return saved.getId();
    }
}
