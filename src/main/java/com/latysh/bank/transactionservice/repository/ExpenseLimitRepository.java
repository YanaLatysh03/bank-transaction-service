package com.latysh.bank.transactionservice.repository;

import com.latysh.bank.transactionservice.entity.ExpenseCategory;
import com.latysh.bank.transactionservice.entity.ExpenseLimit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface ExpenseLimitRepository extends JpaRepository<ExpenseLimit, Long> {
    Optional<ExpenseLimit> findFirstByAccountFromAndExpenseCategoryAndLimitDatetimeLessThanEqualOrderByLimitDatetimeDesc
        (String accountFrom, ExpenseCategory expenseCategory, OffsetDateTime datetime);

    List<ExpenseLimit> findAllByAccountFromOrderByLimitDatetimeDesc(String accountFrom);
}
