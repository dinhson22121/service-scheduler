package com.keyloop.scheduler.availability;

import static com.keyloop.scheduler.support.TestDates.london;
import static com.keyloop.scheduler.support.TestDates.workingDay;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import com.keyloop.scheduler.support.Api;
import com.keyloop.scheduler.support.IntegrationTest;
import org.junit.jupiter.api.Test;

class AvailabilityIntegrationTest extends IntegrationTest {

    private final LocalDate day = workingDay(7);

    private Api.Response availability(long dealershipId, long serviceTypeId) throws Exception {
        return api.get("/api/v1/dealerships/%d/availability?serviceTypeId=%d&date=%s"
                .formatted(dealershipId, serviceTypeId, day));
    }

    @Test
    void listsEveryStartTimeThatFitsWhenNothingIsBooked() throws Exception {
        Api.Response response = availability(LONDON, WHEEL_ALIGNMENT);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body().get("timezone").asString()).isEqualTo("Europe/London");
        assertThat(response.body().get("durationMinutes").asLong()).isEqualTo(90);
        assertThat(response.body().get("slots").size()).isEqualTo(35);
        assertThat(firstStart(response)).isEqualTo(london(day, "08:00").toInstant().toString());
    }

    @Test
    void hidesStartTimesThatWouldOverlapABooking() throws Exception {
        book(booking(LONDON, WHEEL_ALIGNMENT, london(day, "09:00")));

        Api.Response response = availability(LONDON, WHEEL_ALIGNMENT);

        assertThat(response.body().get("slots").size()).isEqualTo(25);
        assertThat(firstStart(response)).isEqualTo(london(day, "10:30").toInstant().toString());
    }

    @Test
    void theLastStartTimeFollowsTheQualifiedTechniciansShifts() throws Exception {
        Api.Response response = availability(LONDON, BRAKE_SERVICE);

        var slots = response.body().get("slots");
        assertThat(slots.get(slots.size() - 1).get("startTime").asString())
                .isEqualTo(london(day, "14:00").toInstant().toString());
    }

    @Test
    void anAdvertisedSlotCanBeBooked() throws Exception {
        String start = firstStart(availability(LONDON, ANNUAL_SERVICE));

        Api.Response booked = book(booking(LONDON, ANNUAL_SERVICE, java.time.OffsetDateTime.parse(start)));

        assertThat(booked.status()).isEqualTo(201);
    }

    @Test
    void unknownDealershipOrServiceTypeIsNotFound() throws Exception {
        assertThat(availability(99, OIL_CHANGE).status()).isEqualTo(404);
        assertThat(availability(LONDON, 99).status()).isEqualTo(404);
    }

    private static String firstStart(Api.Response response) {
        return response.body().get("slots").get(0).get("startTime").asString();
    }
}
