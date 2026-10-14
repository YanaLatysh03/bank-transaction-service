package com.latysh.bank.transactionservice.endtoend;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.latysh.bank.transactionservice.entity.ExpenseCategory;
import com.latysh.bank.transactionservice.rq.CreateLimitRq;
import com.latysh.bank.transactionservice.rq.IncomingTransactionRq;
import com.latysh.bank.transactionservice.rs.ExceededTransactionRs;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static com.github.tomakehurst.wiremock.client.WireMock.get;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Testcontainers
public class CaseOneApiTest {
    private static final String ACCOUNT = "0000000123";
    private static final String COUNTERPARTY = "9999999999";
    private static final ZoneId MINSK = ZoneId.of("Europe/Minsk");
    private static final AdjustableClock CLOCK = new AdjustableClock(MINSK);

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:15");

    @Container
    @ServiceConnection(name = "redis")
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7")
        .withExposedPorts(6379);

    static final WireMockServer frankfurter =
        new WireMockServer(WireMockConfiguration.options().dynamicPort());

    @DynamicPropertySource
    static void frankfurterUrl(DynamicPropertyRegistry registry) {
        if (!frankfurter.isRunning()) {
            frankfurter.start();
        }
        registry.add("frankfurter.base-url", frankfurter::baseUrl);
    }

    @AfterAll
    static void stopFrankfurter() {
        frankfurter.stop();
    }

    @Autowired
    RestTestClient http;

    @Test
    void returnsJanuary3AndJanuary13ExceededTransactions() {
        frankfurter.stubFor(get(urlPathEqualTo("/v2/rate/kzt/usd"))
            .willReturn(okJson("""
                {"date":"2022-01-02","base":"KZT","quote":"USD","rate":1}
                """)));

        CLOCK.set(OffsetDateTime.parse("2022-01-01T00:00:00+03:00"));

        createLimit("1000.00");

        send("500.00", "2022-01-02T10:00:00+03:00");
        send("600.00", "2022-01-03T10:00:00+03:00");

        CLOCK.set(OffsetDateTime.parse("2022-01-10T00:00:00+03:00"));

        createLimit("2000.00");

        send("100.00", "2022-01-11T10:00:00+03:00");
        send("700.00", "2022-01-12T10:00:00+03:00");
        send("100.00", "2022-01-13T10:00:00+03:00");
        send("100.00", "2022-01-13T11:00:00+03:00");

        List<ExceededTransactionRs> exceeded = http.get()
            .uri("/api/client/transactions/" + ACCOUNT+ "/exceeded")
            .exchange()
            .expectStatus().isOk()
            .returnResult(new ParameterizedTypeReference<List<ExceededTransactionRs>>() {})
            .getResponseBody();

        assertThat(exceeded).hasSize(2);
        assertExceeded(exceeded.get(0), LocalDate.of(2022, 1, 3), "600.00",
            "1000.00", "2022-01-01T00:00:00+03:00");
        assertExceeded(exceeded.get(1), LocalDate.of(2022, 1, 13), "100.00",
            "2000.00", "2022-01-10T00:00:00+03:00");
    }

    private void createLimit(String sum) {
        http.post().uri("/api/client/limits")
            .body(new CreateLimitRq(ACCOUNT, ExpenseCategory.PRODUCT, new BigDecimal(sum)))
            .exchange()
            .expectStatus().isCreated();
    }

    private void send(String sum, String datetime) {
        http.post().uri("/api/integration/transactions")
            .body(new IncomingTransactionRq(
                ACCOUNT,
                COUNTERPARTY,
                "KZT",
                new BigDecimal(sum),
                ExpenseCategory.PRODUCT,
                OffsetDateTime.parse(datetime)
            ))
            .exchange()
            .expectStatus().isCreated();
    }

    private static void assertExceeded(ExceededTransactionRs row, LocalDate day, String sum,
                                       String limitSum, String limitDatetime) {
        assertThat(row.accountFrom()).isEqualTo(ACCOUNT);
        assertThat(row.expenseCategory()).isEqualTo(ExpenseCategory.PRODUCT);
        assertThat(row.datetime().atZoneSameInstant(MINSK).toLocalDate()).isEqualTo(day);
        assertThat(row.sum()).isEqualByComparingTo(sum);
        assertThat(row.limitSum()).isEqualByComparingTo(limitSum);
        assertThat(row.limitDatetime().toInstant())
            .isEqualTo(OffsetDateTime.parse(limitDatetime).toInstant());
        assertThat(row.limitCurrencyShortname()).isEqualTo("USD");
    }

    @TestConfiguration
    static class ClockOverride {
        @Bean
        @Primary
        Clock testClock() {
            return CLOCK;
        }
    }

    static final class AdjustableClock extends Clock {
        private final ZoneId zone;
        private volatile Instant instant = Instant.EPOCH;

        AdjustableClock(ZoneId zone) {
            this.zone = zone;
        }

        void set(OffsetDateTime time) {
            this.instant = time.toInstant();
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

}
