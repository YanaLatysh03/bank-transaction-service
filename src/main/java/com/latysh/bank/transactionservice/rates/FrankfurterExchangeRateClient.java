package com.latysh.bank.transactionservice.rates;

import com.latysh.bank.transactionservice.exception.RateMissing;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class FrankfurterExchangeRateClient implements ExchangeRateClient{

    private final FrankfurterApi frankfurterApi;

    @Override
    public Optional<BigDecimal> getExchangeRates(String base, String quote, LocalDate date) {
        try {
            FrankfurterRateRs body = frankfurterApi.getRate(
                base.toLowerCase(),
                quote.toLowerCase(),
                date
            );
            if (body == null || body.rate() == null || body.rate().signum() <= 0) {
                return Optional.empty();
            }
            return Optional.of(body.rate());
        } catch (RateMissing | FeignException missing) {
            return Optional.empty();
        }
    }
}
