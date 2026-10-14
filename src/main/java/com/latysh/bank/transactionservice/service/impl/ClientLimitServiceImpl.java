package com.latysh.bank.transactionservice.service.impl;

import com.latysh.bank.transactionservice.entity.BankTransaction;
import com.latysh.bank.transactionservice.entity.ExpenseLimit;
import com.latysh.bank.transactionservice.repository.BankTransactionRepository;
import com.latysh.bank.transactionservice.repository.ExpenseLimitRepository;
import com.latysh.bank.transactionservice.rq.CreateLimitRq;
import com.latysh.bank.transactionservice.rq.GetClientDataRq;
import com.latysh.bank.transactionservice.rs.ExceededTransactionRs;
import com.latysh.bank.transactionservice.rs.LimitRs;
import com.latysh.bank.transactionservice.service.ClientLimitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClientLimitServiceImpl implements ClientLimitService {

    private final ExpenseLimitRepository expenseLimitRepository;
    private final BankTransactionRepository bankTransactionRepository;
    private final Clock clock;

    @Transactional
    @Override
    public LimitRs createLimit(CreateLimitRq request) {
        var limit = new ExpenseLimit(
            request.accountFrom(),
            request.expenseCategory(),
            request.limitSum(),
            OffsetDateTime.now(clock)
        );

        var savedLimit = expenseLimitRepository.saveAndFlush(limit);
        log.info("Created expense limit {} for account {} category {} sum {}",
            savedLimit.getId(), savedLimit.getAccountFrom(), savedLimit.getExpenseCategory(), savedLimit.getLimitSum());

        return toResponse(savedLimit);
    }

    @Transactional
    @Override
    public List<LimitRs> findAllLimitsByAccountFrom(String accountFrom) {
        log.debug("Loading expense limits for account {}", accountFrom);
        return expenseLimitRepository.findAllByAccountFromOrderByLimitDatetimeDesc(accountFrom).stream()
            .map(this::toResponse)
            .toList();
    }

    @Override
    public List<ExceededTransactionRs> findLimitExceededTransactionsByAccountFrom(String accountFrom) {
        log.debug("Loading limit-exceeded transactions for account {}", accountFrom);
        return bankTransactionRepository.findExceededTransactionsByAccountFrom(accountFrom).stream()
            .map(t -> toExceeded(t)).toList();
    }

    private LimitRs toResponse(ExpenseLimit limit) {
        return new LimitRs(
            limit.getId(),
            limit.getAccountFrom(),
            limit.getExpenseCategory(),
            limit.getLimitSum(),
            limit.getLimitDatetime(),
            limit.getCurrencyShortname()
        );
    }

    private ExceededTransactionRs toExceeded(BankTransaction transaction) {
        ExpenseLimit limit = transaction.getExpenseLimit();
        return new ExceededTransactionRs(
            transaction.getAccountFrom(),
            transaction.getAccountTo(),
            transaction.getCurrencyShortname(),
            transaction.getSum(),
            transaction.getExpenseCategory(),
            transaction.getDatetime(),
            limit == null ? new BigDecimal("1000.00") : limit.getLimitSum(),
            limit == null ? null : limit.getLimitDatetime(),
            limit == null ? "USD" : limit.getCurrencyShortname()
        );
    }
}
