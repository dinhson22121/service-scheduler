package com.keyloop.scheduler.booking;

import static com.keyloop.scheduler.support.TestDates.at;
import static com.keyloop.scheduler.support.TestDates.london;
import static com.keyloop.scheduler.support.TestDates.workingDay;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import com.keyloop.scheduler.support.Api;
import com.keyloop.scheduler.support.IntegrationTest;
import com.keyloop.scheduler.support.TestDates;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BookingApiIntegrationTest extends IntegrationTest {

    private static final String APPOINTMENTS = "/api/v1/appointments";
    private static final long ALIGNMENT_BAY = 3;
    private static final long ALICE = 1;
    private static final long BEN = 2;
    private static final long CHLOE = 3;

    private final LocalDate day = workingDay(7);

    @Nested
    class Booking {

        @Test
        void assignsABayAndAQualifiedTechnicianOnShiftAndPersistsTheAppointment() throws Exception {
            Api.Response response = book(booking(LONDON, WHEEL_ALIGNMENT, london(day, "09:00")));

            assertThat(response.status()).isEqualTo(201);
            assertThat(response.header("Idempotent-Replayed")).isEqualTo("false");
            assertThat(response.header("X-Trace-Id")).isNotBlank();
            assertThat(response.body().get("status").asString()).isEqualTo("CONFIRMED");
            assertThat(response.body().get("serviceBayId").asLong()).isEqualTo(ALIGNMENT_BAY);
            assertThat(response.body().get("technicianId").asLong()).isEqualTo(CHLOE);
            assertThat(response.body().get("startTime").asString()).isEqualTo(london(day, "09:00").toInstant().toString());
            assertThat(response.body().get("endTime").asString()).isEqualTo(london(day, "10:30").toInstant().toString());

            Api.Response fetched = api.get(response.header("Location"));
            assertThat(fetched.status()).isEqualTo(200);
            assertThat(fetched.body()).isEqualTo(response.body());
        }

        @Test
        void picksTheTechnicianWhoseShiftCoversTheWholeSlot() throws Exception {
            Api.Response response = book(booking(LONDON, WHEEL_ALIGNMENT, london(day, "16:00")));

            assertThat(response.status()).isEqualTo(201);
            assertThat(response.body().get("technicianId").asLong()).isEqualTo(BEN);
        }

        @Test
        void rejectsWhenNoQualifiedTechnicianIsOnShift() throws Exception {
            Api.Response response = book(booking(LONDON, BRAKE_SERVICE, london(day, "15:00")));

            assertThat(response.status()).isEqualTo(409);
            assertThat(response.body().get("detail").asString()).contains("No technician qualified for BRAKES");
        }

        @Test
        void rejectsAnOverlappingBookingOfTheOnlySuitableBay() throws Exception {
            assertThat(book(booking(LONDON, WHEEL_ALIGNMENT, london(day, "10:00"))).status()).isEqualTo(201);

            Api.Response overlapping = book(booking(LONDON, 2, 3, WHEEL_ALIGNMENT, london(day, "11:00")));

            assertThat(overlapping.status()).isEqualTo(409);
            assertThat(overlapping.header("Content-Type")).contains("application/problem+json");
            assertThat(overlapping.body().get("detail").asString()).contains("No ALIGNMENT service bay");
            assertThat(confirmedCount()).isEqualTo(1);
        }

        @Test
        void acceptsBackToBackBookingsOfTheSameBay() throws Exception {
            assertThat(book(booking(LONDON, WHEEL_ALIGNMENT, london(day, "10:00"))).status()).isEqualTo(201);

            Api.Response next = book(booking(LONDON, 2, 3, WHEEL_ALIGNMENT, london(day, "11:30")));

            assertThat(next.status()).isEqualTo(201);
            assertThat(next.body().get("serviceBayId").asLong()).isEqualTo(ALIGNMENT_BAY);
        }

        @Test
        void usesAnotherBayAndTechnicianWhenTheFirstPairIsTaken() throws Exception {
            Api.Response first = book(booking(LONDON, OIL_CHANGE, london(day, "10:00")));
            Api.Response second = book(booking(LONDON, 2, 3, OIL_CHANGE, london(day, "10:00")));

            assertThat(second.status()).isEqualTo(201);
            assertThat(second.body().get("serviceBayId").asLong())
                    .isNotEqualTo(first.body().get("serviceBayId").asLong());
            assertThat(second.body().get("technicianId").asLong())
                    .isNotEqualTo(first.body().get("technicianId").asLong())
                    .isIn(ALICE, BEN);
        }

        @Test
        void resolvesOpeningHoursAndShiftsInTheDealershipsTimeZone() throws Exception {
            Api.Response response = book(booking(SAIGON, OIL_CHANGE, at(day, "07:30", TestDates.SAIGON)));

            assertThat(response.status()).isEqualTo(201);
            assertThat(response.body().get("startTime").asString())
                    .isEqualTo(at(day, "07:30", TestDates.SAIGON).toInstant().toString());
        }

        @Test
        void rejectsAServiceTheDealershipHasNoBayFor() throws Exception {
            Api.Response response = book(booking(SAIGON, WHEEL_ALIGNMENT, at(day, "09:00", TestDates.SAIGON)));

            assertThat(response.status()).isEqualTo(409);
        }
    }

    @Nested
    class OneAppointmentPerVehicleAtATime {

        @Test
        void sendingTheSameRequestAgainReturnsTheOriginalAppointment() throws Exception {
            Map<String, Object> body = booking(LONDON, OIL_CHANGE, london(day, "09:00"));

            Api.Response first = book(body);
            Api.Response retry = book(body);

            assertThat(retry.status()).isEqualTo(201);
            assertThat(retry.header("Idempotent-Replayed")).isEqualTo("true");
            assertThat(retry.body().get("id")).isEqualTo(first.body().get("id"));
            assertThat(confirmedCount()).isEqualTo(1);
        }

        @Test
        void aDifferentBookingThatOverlapsTheVehiclesAppointmentIsRejected() throws Exception {
            book(booking(LONDON, OIL_CHANGE, london(day, "09:00")));

            Api.Response overlapping = book(booking(LONDON, WHEEL_ALIGNMENT, london(day, "09:30")));

            assertThat(overlapping.status()).isEqualTo(409);
            assertThat(overlapping.body().get("title").asString()).isEqualTo("Vehicle already booked");
            assertThat(overlapping.body().get("detail").asString()).contains("Vehicle 1 already has an appointment");
            assertThat(confirmedCount()).isEqualTo(1);
        }

        @Test
        void theVehicleCanBeBookedBackToBack() throws Exception {
            assertThat(book(booking(LONDON, OIL_CHANGE, london(day, "09:00"))).status()).isEqualTo(201);

            assertThat(book(booking(LONDON, OIL_CHANGE, london(day, "10:00"))).status()).isEqualTo(201);
            assertThat(confirmedCount()).isEqualTo(2);
        }

        @Test
        void aCancelledAppointmentNoLongerHoldsTheVehicle() throws Exception {
            Map<String, Object> body = booking(LONDON, OIL_CHANGE, london(day, "09:00"));
            String cancelledId = book(body).body().get("id").asString();
            api.post(APPOINTMENTS + "/" + cancelledId + "/cancel");

            Api.Response rebooked = book(body);

            assertThat(rebooked.status()).isEqualTo(201);
            assertThat(rebooked.header("Idempotent-Replayed")).isEqualTo("false");
            assertThat(rebooked.body().get("id").asString()).isNotEqualTo(cancelledId);
        }
    }

    @Nested
    class Validation {

        @Test
        void rejectsAMalformedBody() throws Exception {
            Map<String, Object> body = booking(LONDON, OIL_CHANGE, london(day, "09:00"));
            body.remove("serviceTypeId");
            body.put("vehicleId", -1);

            Api.Response response = book(body);

            assertThat(response.status()).isEqualTo(400);
            assertThat(response.header("Content-Type")).contains("application/problem+json");
        }

        @Test
        void rejectsAStartTimeWithoutOffset() throws Exception {
            Map<String, Object> body = booking(LONDON, OIL_CHANGE, london(day, "09:00"));
            body.put("startTime", day + "T09:00:00");

            assertThat(book(body).status()).isEqualTo(400);
        }

        @Test
        void unknownReferencesAreNotFound() throws Exception {
            OffsetDateTime start = london(day, "09:00");

            assertThat(book(booking(99, 1, 1, OIL_CHANGE, start)).status()).isEqualTo(404);
            assertThat(book(booking(LONDON, 1, 1, 99, start)).status()).isEqualTo(404);
            assertThat(book(booking(LONDON, 99, 1, OIL_CHANGE, start)).status()).isEqualTo(404);
            assertThat(book(booking(LONDON, 1, 99, OIL_CHANGE, start)).status()).isEqualTo(404);
        }

        @Test
        void theVehicleMustBelongToTheCustomer() throws Exception {
            Api.Response response = book(booking(LONDON, 2, 1, OIL_CHANGE, london(day, "09:00")));

            assertThat(response.status()).isEqualTo(422);
            assertThat(response.body().get("detail").asString()).contains("does not belong");
        }

        @Test
        void rejectsAStartInThePast() throws Exception {
            Api.Response response = book(booking(LONDON, OIL_CHANGE, OffsetDateTime.now().minusHours(1)));

            assertThat(response.status()).isEqualTo(422);
        }

        @Test
        void theServiceMustFitInsideOpeningHours() throws Exception {
            assertThat(book(booking(LONDON, WHEEL_ALIGNMENT, london(day, "17:00"))).status()).isEqualTo(422);
            assertThat(book(booking(LONDON, OIL_CHANGE, london(day, "07:30"))).status()).isEqualTo(422);
            assertThat(confirmedCount()).isZero();
        }
    }

    @Nested
    class Reading {

        @Test
        void listsTheDealershipsAppointmentsOfALocalDayInStartOrder() throws Exception {
            book(booking(LONDON, OIL_CHANGE, london(day, "14:00")));
            book(booking(LONDON, WHEEL_ALIGNMENT, london(day, "09:00")));
            book(booking(LONDON, OIL_CHANGE, london(day.plusDays(1), "09:00")));

            Api.Response list = api.get("/api/v1/dealerships/1/appointments?date=" + day);

            assertThat(list.status()).isEqualTo(200);
            assertThat(list.body().size()).isEqualTo(2);
            assertThat(list.body().get(0).get("serviceTypeId").asLong()).isEqualTo(WHEEL_ALIGNMENT);
        }

        @Test
        void unknownOrMalformedLookupsAreRejected() throws Exception {
            assertThat(api.get(APPOINTMENTS + "/" + UUID.randomUUID()).status()).isEqualTo(404);
            assertThat(api.get(APPOINTMENTS + "/not-a-uuid").status()).isEqualTo(400);
            assertThat(api.get("/api/v1/dealerships/99/appointments?date=" + day).status()).isEqualTo(404);
            assertThat(api.get("/api/v1/dealerships/1/appointments").status()).isEqualTo(400);
        }
    }

    @Nested
    class Cancellation {

        @Test
        void cancellingFreesTheBayAndTechnician() throws Exception {
            Api.Response booked = book(booking(LONDON, WHEEL_ALIGNMENT, london(day, "09:00")));
            String id = booked.body().get("id").asString();

            Api.Response cancelled = api.post(APPOINTMENTS + "/" + id + "/cancel");

            assertThat(cancelled.status()).isEqualTo(200);
            assertThat(cancelled.body().get("status").asString()).isEqualTo("CANCELLED");
            assertThat(cancelled.body().get("cancelledAt").isNull()).isFalse();
            assertThat(book(booking(LONDON, 2, 3, WHEEL_ALIGNMENT, london(day, "09:00"))).status()).isEqualTo(201);
        }

        @Test
        void cancellingTwiceIsHarmless() throws Exception {
            String id = book(booking(LONDON, OIL_CHANGE, london(day, "09:00"))).body().get("id").asString();
            Api.Response first = api.post(APPOINTMENTS + "/" + id + "/cancel");

            Api.Response second = api.post(APPOINTMENTS + "/" + id + "/cancel");

            assertThat(second.status()).isEqualTo(200);
            assertThat(second.body()).isEqualTo(first.body());
        }

        @Test
        void anAppointmentThatHasStartedCannotBeCancelled() throws Exception {
            UUID id = UUID.randomUUID();
            jdbc.sql("""
                            INSERT INTO appointment (id, dealership_id, customer_id, vehicle_id, service_type_id,
                                service_bay_id, technician_id, starts_at, ends_at, status, created_at)
                            VALUES (:id, 1, 1, 1, 1, 1, 1, now() - interval '10 minutes', now() + interval '50 minutes',
                                'CONFIRMED', now())""")
                    .param("id", id)
                    .update();

            Api.Response response = api.post(APPOINTMENTS + "/" + id + "/cancel");

            assertThat(response.status()).isEqualTo(409);
        }

        @Test
        void cancellingAnUnknownAppointmentIsNotFound() throws Exception {
            assertThat(api.post(APPOINTMENTS + "/" + UUID.randomUUID() + "/cancel").status()).isEqualTo(404);
        }
    }
}
