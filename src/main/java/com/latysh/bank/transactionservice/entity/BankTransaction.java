package com.latysh.bank.transactionservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Entity
@Table(name = "bank_transaction")
@NoArgsConstructor
public class BankTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_from", nullable = false, length = 64)
    private String accountFrom;

    @Column(name = "account_to", nullable = false, length = 64)
    private String accountTo;

    @Column(name = "currency_shortname", nullable = false, length = 3)
    private String currencyShortname;

    @Column(name = "sum", nullable = false, precision = 19, scale = 2)
    private BigDecimal sum;

    @Column(name = "expense_category", nullable = false, length = 16)
    private ExpenseCategory expenseCategory;

    @Column(name = "datetime", nullable = false)
    private OffsetDateTime datetime;

    @Column(name = "sum_usd", nullable = false, precision = 19, scale = 2)
    private BigDecimal sumUsd;

    @Column(name = "limit_exceeded", nullable = false)
    private boolean limitExceeded;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expense_limit_id")
    private ExpenseLimit expenseLimit;

    public BankTransaction(String accountFrom, String accountTo, String currencyShortname,
                           BigDecimal sum, ExpenseCategory expenseCategory, OffsetDateTime datetime,
                           BigDecimal sumUsd, boolean limitExceeded, ExpenseLimit expenseLimit) {
        this.accountFrom = accountFrom;
        this.accountTo = accountTo;
        this.currencyShortname = currencyShortname;
        this.sum = sum;
        this.expenseCategory = expenseCategory;
        this.datetime = datetime;
        this.sumUsd = sumUsd;
        this.limitExceeded = limitExceeded;
        this.expenseLimit = expenseLimit;
    }
}
