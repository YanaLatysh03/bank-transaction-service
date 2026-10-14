package com.latysh.bank.transactionservice.repository;

import com.latysh.bank.transactionservice.entity.BankTransaction;
import com.latysh.bank.transactionservice.entity.ExpenseCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public interface BankTransactionRepository extends JpaRepository<BankTransaction, Long> {

    @Query("""
        SELECT SUM (bt.sumUsd)
        FROM BankTransaction bt
        WHERE bt.accountFrom = :accountFrom
        AND bt.expenseCategory = :category
        AND  bt.datetime >= :monthStart
        AND bt.datetime < :monthEnd
        """)
    BigDecimal sumUsd(@Param("accountFrom") String accountFrom,
                      @Param("category") ExpenseCategory category,
                      @Param("monthStart") OffsetDateTime monthStart,
                      @Param("monthEnd") OffsetDateTime monthEnd);

    @Query("""
        SELECT bt
        FROM BankTransaction bt
        LEFT JOIN FETCH bt.expenseLimit
        WHERE bt.limitExceeded = true
        AND bt.accountFrom = :accountFrom
        ORDER BY bt.datetime
        """)
    List<BankTransaction> findExceededTransactionsByAccountFrom(
        @Param("accountFrom") String accountFrom);
}
