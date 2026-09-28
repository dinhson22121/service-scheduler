package com.keyloop.scheduler;

import static com.keyloop.scheduler.support.TestDates.london;
import static com.keyloop.scheduler.support.TestDates.workingDay;
import static org.assertj.core.api.Assertions.assertThat;

import com.keyloop.scheduler.support.Api;
import com.keyloop.scheduler.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

class ObservabilityIntegrationTest extends IntegrationTest {

    @Value("${local.management.port}")
    private int managementPort;

    private Api management;

    @BeforeEach
    void managementClient() {
        management = new Api("http://localhost:" + managementPort);
    }

    @Test
    void probesAreUpOnTheManagementPort() throws Exception {
        assertThat(management.get("/actuator/health/liveness").status()).isEqualTo(200);
        Api.Response readiness = management.get("/actuator/health/readiness");
        assertThat(readiness.status()).isEqualTo(200);
        assertThat(readiness.body().get("status").asString()).isEqualTo("UP");
        assertThat(readiness.body().has("components")).as("health details are not public").isFalse();
    }

    @Test
    void actuatorIsNotServedOnTheApplicationPort() throws Exception {
        assertThat(api.get("/actuator/health").status()).isEqualTo(404);
    }

    @Test
    void businessCountersAreExported() throws Exception {
        book(booking(LONDON, OIL_CHANGE, london(workingDay(7), "09:00")));

        String metrics = management.getText("/actuator/prometheus");

        assertThat(metrics).contains("scheduler_bookings_total{outcome=\"confirmed\"");
        assertThat(metrics).contains("scheduler_booking_conflicts_total");
        assertThat(metrics).contains("http_server_requests_seconds_bucket");
    }

    @Test
    void theApiContractIsPublished() throws Exception {
        Api.Response openApi = api.get("/v3/api-docs");

        assertThat(openApi.status()).isEqualTo(200);
        assertThat(openApi.body().get("paths").has("/api/v1/appointments")).isTrue();
    }
}
