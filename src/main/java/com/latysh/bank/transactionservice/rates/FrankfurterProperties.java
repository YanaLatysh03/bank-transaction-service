package com.latysh.bank.transactionservice.rates;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "frankfurter")
public record FrankfurterProperties(
    String baseUrl,
    Duration connectTimeout,
    Duration readTimeout,
    int attempts
) {
}
