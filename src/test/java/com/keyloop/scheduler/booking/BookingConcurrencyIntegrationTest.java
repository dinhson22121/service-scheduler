package com.keyloop.scheduler.booking;

import static com.keyloop.scheduler.support.TestDates.london;
import static com.keyloop.scheduler.support.TestDates.workingDay;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntFunction;

import com.keyloop.scheduler.support.Api;
import com.keyloop.scheduler.support.IntegrationTest;
import org.junit.jupiter.api.Test;

class BookingConcurrencyIntegrationTest extends IntegrationTest {

    private static final int CONCURRENT_REQUESTS = 50;

    private final LocalDate day = workingDay(14);

    @Test
    void onlyOneOfManyConcurrentRequestsGetsTheOnlyAlignmentBay() throws Exception {
        List<Api.Response> responses = fireConcurrently(CONCURRENT_REQUESTS,
                i -> fleetBooking(i, LONDON, WHEEL_ALIGNMENT, london(day, "10:00")));

        assertThat(statuses(responses, 201)).isEqualTo(1);
        assertThat(statuses(responses, 409)).isEqualTo(CONCURRENT_REQUESTS - 1);
        assertThat(confirmedCount()).isEqualTo(1);
    }

    @Test
    void concurrentRequestsFillExactlyTheFreeCapacity() throws Exception {
        List<Api.Response> responses = fireConcurrently(CONCURRENT_REQUESTS,
                i -> fleetBooking(i, LONDON, OIL_CHANGE, london(day, "10:00")));

        List<Api.Response> winners = responses.stream().filter(r -> r.status() == 201).toList();
        assertThat(winners).hasSize(2);
        assertThat(winners).extracting(r -> r.body().get("serviceBayId").asLong()).doesNotHaveDuplicates();
        assertThat(winners).extracting(r -> r.body().get("technicianId").asLong()).doesNotHaveDuplicates();
        assertThat(statuses(responses, 409)).isEqualTo(CONCURRENT_REQUESTS - 2);
        assertThat(confirmedCount()).isEqualTo(2);
    }

    @Test
    void theSameRequestSentConcurrentlyCreatesOneAppointment() throws Exception {
        Map<String, Object> body = booking(LONDON, OIL_CHANGE, london(day, "09:00"));

        List<Api.Response> responses = fireConcurrently(20, i -> body);

        assertThat(statuses(responses, 201)).isEqualTo(20);
        assertThat(responses).extracting(r -> r.body().get("id").asString()).containsOnly(
                responses.getFirst().body().get("id").asString());
        assertThat(responses).filteredOn(r -> "false".equals(r.header("Idempotent-Replayed"))).hasSize(1);
        assertThat(confirmedCount()).isEqualTo(1);
    }

    @Test
    void concurrentDifferentBookingsOfOneVehicleNeverBothWin() throws Exception {
        List<Api.Response> responses = fireConcurrently(20, i ->
                fleetBooking(0, LONDON, i % 2 == 0 ? OIL_CHANGE : WHEEL_ALIGNMENT, london(day, "10:00")));

        assertThat(responses).allSatisfy(r -> assertThat(r.status()).isIn(201, 409));
        assertThat(responses).filteredOn(r -> r.status() == 201)
                .extracting(r -> r.body().get("id").asString()).containsOnly(
                        responses.stream().filter(r -> r.status() == 201).findFirst().orElseThrow()
                                .body().get("id").asString());
        assertThat(responses).filteredOn(r -> r.status() == 409)
                .allSatisfy(r -> assertThat(r.body().get("title").asString()).isEqualTo("Vehicle already booked"));
        assertThat(confirmedCount()).isEqualTo(1);
    }

    private List<Api.Response> fireConcurrently(int count, IntFunction<Map<String, Object>> request) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Api.Response>> futures = new ArrayList<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < count; i++) {
                Map<String, Object> body = request.apply(i);
                futures.add(executor.submit(() -> {
                    start.await();
                    return api.post("/api/v1/appointments", body);
                }));
            }
            start.countDown();
            List<Api.Response> responses = new ArrayList<>();
            for (Future<Api.Response> future : futures) {
                responses.add(future.get());
            }
            return responses;
        }
    }

    private static long statuses(List<Api.Response> responses, int status) {
        return responses.stream().filter(r -> r.status() == status).count();
    }
}
