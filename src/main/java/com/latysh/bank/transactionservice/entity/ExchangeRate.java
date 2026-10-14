package com.latysh.bank.transactionservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Entity
@Table(
    name = "exchange_rate",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_exchange_rate_pair_date",
        columnNames = {"base", "quote", "rate_date"}
    )
)
@NoArgsConstructor()
public class ExchangeRate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 3)
    private String base;

    @Column(nullable = false, length = 3)
    private String quote;

    @Column(name = "rate_date", nullable = false)
    private LocalDate rateDate;

    @Column(name = "close", nullable = false, precision = 19, scale = 8)
    private BigDecimal close;

    public ExchangeRate(String base, String quote, LocalDate rateDate, BigDecimal close) {
        this.base = base;
        this.quote = quote;
        this.rateDate = rateDate;
        this.close = close;
    }
}
