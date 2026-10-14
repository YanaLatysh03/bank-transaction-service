package com.latysh.bank.transactionservice.rates;

import com.latysh.bank.transactionservice.exception.RateMissing;
import feign.*;
import feign.jackson3.Jackson3Decoder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(FrankfurterProperties.class)
public class FrankfurterConfig {
    @Bean
    FrankfurterApi frankfurterApi(FrankfurterProperties properties) {
        return Feign.builder()
            .decoder(new Jackson3Decoder())
            .options(new Request.Options(properties.connectTimeout(), properties.readTimeout(), true))
            .retryer(new Retryer.Default(200, 1000, properties.attempts()))
            .errorDecoder((methodKey, response) -> {
                int status = response.status();
                if (status == 404 || status == 422) {
                    return new RateMissing();
                }
                if (status >= 500) {
                    return new RetryableException(
                        status,
                        "Frankfurter responded with " + status,
                        response.request().httpMethod(),
                        (Long) null,
                        response.request()
                    );
                }
                return FeignException.errorStatus(methodKey, response);
            })
            .target(FrankfurterApi.class, properties.baseUrl());
    }
}
