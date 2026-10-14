package com.latysh.bank.transactionservice.rates;

import com.latysh.bank.transactionservice.exception.RateMissing;
import feign.FeignException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class FrankfurterExchangeRateClient implements ExchangeRateClient {

    private static final String METRIC = "frankfurter.requests";

    private final FrankfurterApi frankfurterApi;
    private final MeterRegistry meterRegistry;

    @Override
    public Optional<BigDecimal> getExchangeRates(String base, String quote, LocalDate date) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String outcome = "success";
        try {
            FrankfurterRateRs body = frankfurterApi.getRate(
                base.toLowerCase(),
                quote.toLowerCase(),
                date
            );
            if (body == null || body.rate() == null || body.rate().signum() <= 0) {
                outcome = "empty";
                return Optional.empty();
            }
            return Optional.of(body.rate());
        } catch (RateMissing missing) {
            outcome = "missing";
            return Optional.empty();
        } catch (FeignException failed) {
            outcome = "error";
            return Optional.empty();
        } finally {
            sample.stop(Timer.builder(METRIC)
                .description("Calls to the Frankfurter exchange rate API")
                .tag("outcome", outcome)
                .register(meterRegistry));
        }
    }
}
