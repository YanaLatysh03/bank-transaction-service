package com.latysh.bank.transactionservice.service;

import com.latysh.bank.transactionservice.rq.IncomingTransactionRq;

public interface IntegrationTransactionService {

    long accept(IncomingTransactionRq request);
}
