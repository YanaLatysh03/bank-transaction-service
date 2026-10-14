package com.latysh.bank.transactionservice.config;

import com.latysh.bank.transactionservice.service.impl.IntegrationTransactionServiceImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfig {
    @Bean
    Clock clock() {
        return Clock.system(IntegrationTransactionServiceImpl.MONTH_ZONE);
    }
}
