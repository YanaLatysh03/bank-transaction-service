package com.latysh.bank.transactionservice.cache;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
@Testcontainers
class ExchangeRateCacheIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:15");

    @Container
    @ServiceConnection(name = "redis")
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7")
        .withExposedPorts(6379);

    @Autowired
    ExchangeRateCache cache;

    @Autowired
    StringRedisTemplate template;

    @Test
    void storesCloseInRedisOnTopOfTheDatabase() {
        LocalDate day = LocalDate.of(2022, 1, 2);

        assertThat(cache.get("KZT", "USD", day)).isEmpty();

        cache.put("KZT", "USD", day, new BigDecimal("0.00230000"));

        assertThat(cache.get("KZT", "USD", day))
            .get()
            .usingComparator(BigDecimal::compareTo)
            .isEqualTo(new BigDecimal("0.00230000"));
        assertThat(template.opsForValue().get(RedisExchangeRateCache.key("KZT", "USD", day)))
            .isEqualTo("0.00230000");
        assertThat(template.getExpire(RedisExchangeRateCache.key("KZT", "USD", day)))
            .isCloseTo(Duration.ofHours(24).toSeconds(), within(30L));

        cache.put("EUR", "USD", day, new BigDecimal("1.13000000"));
        assertThat(cache.get("KZT", "USD", day))
            .get()
            .usingComparator(BigDecimal::compareTo)
            .isEqualTo(new BigDecimal("0.00230000"));
    }
}
