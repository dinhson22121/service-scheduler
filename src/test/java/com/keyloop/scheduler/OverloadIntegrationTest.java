package com.keyloop.scheduler;

import static com.keyloop.scheduler.support.TestDates.workingDay;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.time.Duration;

import javax.sql.DataSource;

import com.keyloop.scheduler.support.Api;
import com.keyloop.scheduler.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
        "spring.datasource.hikari.maximum-pool-size=1",
        "spring.datasource.hikari.connection-timeout=250"
})
class OverloadIntegrationTest extends IntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void failsFastWith503WhenNoDatabaseConnectionIsAvailable() throws Exception {
        try (Connection onlyConnection = dataSource.getConnection()) {
            long started = System.nanoTime();

            Api.Response response = api.get("/api/v1/dealerships/1/availability?serviceTypeId=1&date=" + workingDay(7));

            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(2));
            assertThat(response.status()).isEqualTo(503);
            assertThat(response.header("Retry-After")).isEqualTo("1");
            assertThat(response.header("Content-Type")).contains("application/problem+json");
        }
    }
}
