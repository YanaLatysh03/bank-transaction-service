package com.latysh.bank.transactionservice.repository;

import com.latysh.bank.transactionservice.entity.AccountCategoryLock;
import com.latysh.bank.transactionservice.entity.AccountCategoryLockId;
import com.latysh.bank.transactionservice.entity.ExpenseCategory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountCategoryLockRepository extends JpaRepository<AccountCategoryLock, AccountCategoryLockId> {

    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO account_category_lock (account_from, expense_category)
            VALUES (:accountFrom, :expenseCategory)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    void insertIfAbsent(@Param("accountFrom") String accountFrom,
                        @Param("expenseCategory") String expenseCategory);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT lockRow FROM AccountCategoryLock lockRow
            WHERE lockRow.id.accountFrom = :accountFrom
              AND lockRow.id.expenseCategory = :category
            """)
    AccountCategoryLock lock(@Param("accountFrom") String accountFrom,
                             @Param("category") ExpenseCategory category);
}
