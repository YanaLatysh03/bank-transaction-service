package com.latysh.bank.transactionservice.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisExchangeRateCacheTest {
    @Mock
    StringRedisTemplate redis;

    @Mock
    ValueOperations<String, String> values;

    RedisExchangeRateCache cache;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
        cache = new RedisExchangeRateCache(redis, new ExchangeRateCacheProperties(Duration.ofHours(24)));
    }

    @Test
    void returnsEmptyWhenRedisReadFails() {
        when(values.get(anyString())).thenThrow(new RedisConnectionFailureException("down"));

        assertThat(cache.get("KZT", "USD", LocalDate.of(2022, 1, 2))).isEmpty();
    }

    @Test
    void ignoresWriteFailure() {
        doThrow(new RedisConnectionFailureException("down"))
            .when(values).set(anyString(), anyString(), any(Duration.class));

        cache.put("KZT", "USD", LocalDate.of(2022, 1, 2), new BigDecimal("0.00230000"));
    }

    @Test
    void dropsUnreadableValue() {
        String key = RedisExchangeRateCache.key("KZT", "USD", LocalDate.of(2022, 1, 2));
        when(values.get(key)).thenReturn("not-a-number");

        assertThat(cache.get("KZT", "USD", LocalDate.of(2022, 1, 2))).isEmpty();
        verify(redis).delete(key);
    }
}
