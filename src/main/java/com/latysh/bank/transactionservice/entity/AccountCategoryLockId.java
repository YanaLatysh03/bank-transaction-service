package com.latysh.bank.transactionservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class AccountCategoryLockId {
    @Column(name = "account_from", nullable = false, length = 64)
    private String accountFrom;

    @Column(name = "expense_category", nullable = false, length = 16)
    private ExpenseCategory expenseCategory;
}
