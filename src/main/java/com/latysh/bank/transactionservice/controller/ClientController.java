package com.latysh.bank.transactionservice.controller;

import com.latysh.bank.transactionservice.controller.api.ClientApi;
import com.latysh.bank.transactionservice.rq.CreateLimitRq;
import com.latysh.bank.transactionservice.rq.GetClientDataRq;
import com.latysh.bank.transactionservice.rs.ExceededTransactionRs;
import com.latysh.bank.transactionservice.rs.LimitRs;
import com.latysh.bank.transactionservice.service.ClientLimitService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ClientController implements ClientApi {

    private final ClientLimitService clientLimitService;

    @Override
    public LimitRs createLimit(CreateLimitRq request) {
        return clientLimitService.createLimit(request);
    }

    @Override
    public List<LimitRs> findLimits(String accountFrom) {
        return clientLimitService.findAllLimitsByAccountFrom(accountFrom);
    }

    @Override
    public List<ExceededTransactionRs> findExceededLimitTransactions(String accountFrom) {
        return clientLimitService.findLimitExceededTransactionsByAccountFrom(accountFrom);
    }
}
