package com.latysh.bank.transactionservice.cache;

import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "exchange-rate.cache")
public record ExchangeRateCacheProperties(
    @DefaultValue("24h") Duration ttl
) {
}
