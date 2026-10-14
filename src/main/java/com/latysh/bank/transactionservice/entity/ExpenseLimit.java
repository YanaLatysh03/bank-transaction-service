package com.latysh.bank.transactionservice.entity;

import com.latysh.bank.transactionservice.DomainConstants;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;


@Getter
@Entity
@Table(name = "expense_limit")
@NoArgsConstructor
public class ExpenseLimit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_from", nullable = false, length = 64)
    private String accountFrom;

    @Column(name = "expense_category", nullable = false, length = 16)
    private ExpenseCategory expenseCategory;

    // Сумма в долларах
    @Column(name = "limit_sum", nullable = false, precision = 19, scale = 2)
    private BigDecimal limitSum;

    @Column(name = "limit_datetime", nullable = false)
    private OffsetDateTime limitDatetime;

    @Column(name = "currency_shortname", nullable = false, length = 3)
    private String currencyShortname;

    public ExpenseLimit(String accountFrom, ExpenseCategory expenseCategory,
                        BigDecimal limitSum, OffsetDateTime limitDatetime) {
        this.accountFrom = accountFrom;
        this.expenseCategory = expenseCategory;
        this.limitSum = limitSum;
        this.limitDatetime = limitDatetime;
        this.currencyShortname = DomainConstants.USD;
    }
}
