package com.latysh.bank.transactionservice.service;

import com.latysh.bank.transactionservice.rq.CreateLimitRq;
import com.latysh.bank.transactionservice.rq.GetClientDataRq;
import com.latysh.bank.transactionservice.rs.ExceededTransactionRs;
import com.latysh.bank.transactionservice.rs.LimitRs;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ClientLimitService {
    @Transactional
    LimitRs createLimit(CreateLimitRq request);

    @Transactional
    List<LimitRs> findAllLimitsByAccountFrom(String accountFrom);

    List<ExceededTransactionRs> findLimitExceededTransactionsByAccountFrom(String accountFrom);
}
