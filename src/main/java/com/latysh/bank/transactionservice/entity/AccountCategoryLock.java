package com.latysh.bank.transactionservice.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "account_category_lock")
@NoArgsConstructor
public class AccountCategoryLock {
    @EmbeddedId
    private AccountCategoryLockId id;

    public AccountCategoryLock(String accountFrom, ExpenseCategory expenseCategory) {
        this.id = new AccountCategoryLockId(accountFrom, expenseCategory);
    }
}
