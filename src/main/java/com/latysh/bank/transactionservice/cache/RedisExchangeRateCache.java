package com.latysh.bank.transactionservice.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
public class RedisExchangeRateCache implements ExchangeRateCache {
    private static final String KEY_PREFIX = "exchange-rate:";

    private final StringRedisTemplate redis;
    private final ExchangeRateCacheProperties properties;

    @Override
    public Optional<BigDecimal> get(String base, String quote, LocalDate day) {
        String key = key(base, quote, day);
        try {
            String value = redis.opsForValue().get(key);
            if (value == null) {
                return Optional.empty();
            }
            return Optional.of(new BigDecimal(value));
        } catch (NumberFormatException invalid) {
            deleteQuietly(key);
            return Optional.empty();
        } catch (DataAccessException unavailable) {
            log.warn("Exchange rate cache read failed for {} {} {}", base, quote, day, unavailable);
            return Optional.empty();
        }
    }

    @Override
    public void put(String base, String quote, LocalDate day, BigDecimal close) {
        try {
            redis.opsForValue().set(key(base, quote, day), close.toPlainString(), properties.ttl());
        } catch (DataAccessException unavailable) {
            log.warn("Exchange rate cache write failed for {} {} {}", base, quote, day, unavailable);
        }
    }

    private void deleteQuietly(String key) {
        try {
            redis.delete(key);
        } catch (DataAccessException unavailable) {
            log.warn("Exchange rate cache delete failed for {}", key, unavailable);
        }
    }

    static String key(String base, String quote, LocalDate day) {
        return KEY_PREFIX + base + ":" + quote + ":" + day;
    }
}
