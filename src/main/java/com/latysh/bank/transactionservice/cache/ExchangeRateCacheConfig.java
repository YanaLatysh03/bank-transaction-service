package com.latysh.bank.transactionservice.cache;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
@EnableConfigurationProperties(ExchangeRateCacheProperties.class)
public class ExchangeRateCacheConfig {
    @Bean
    ExchangeRateCache exchangeRateCache(StringRedisTemplate redis, ExchangeRateCacheProperties properties) {
        return new RedisExchangeRateCache(redis, properties);
    }
}
