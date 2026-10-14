package com.latysh.bank.transactionservice.repository;

import com.latysh.bank.transactionservice.entity.ExchangeRate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long> {
    Optional<ExchangeRate> findByBaseAndQuoteAndRateDate(String base, String quote, LocalDate rateDate);
    Optional<ExchangeRate> findFirstByBaseAndQuoteAndRateDateLessThanOrderByRateDateDesc(
        String base, String quote, LocalDate rateDate);
}
