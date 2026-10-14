package com.latysh.bank.transactionservice.controller;

import com.latysh.bank.transactionservice.controller.api.IntegrationTransactionApi;
import com.latysh.bank.transactionservice.rq.IncomingTransactionRq;
import com.latysh.bank.transactionservice.rs.AcceptedTransactionRs;
import com.latysh.bank.transactionservice.service.IntegrationTransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class IntegrationTransactionController implements IntegrationTransactionApi {

    private final IntegrationTransactionService transactions;

    @Override
    public AcceptedTransactionRs accept(IncomingTransactionRq request) {
        return new AcceptedTransactionRs(transactions.accept(request));
    }
}
