package com.latysh.bank.transactionservice.service;

import com.latysh.bank.transactionservice.rq.IncomingTransactionRq;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

public interface BankTransactionService {
    @Transactional(isolation = Isolation.READ_COMMITTED)
    long acceptTransaction(IncomingTransactionRq request, BigDecimal sumUsd);
}
