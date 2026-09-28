package com.keyloop.scheduler.booking.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class BookAppointmentRequestTest {

    @Test
    void theCommandIgnoresHowTheStartTimeOffsetIsWritten() {
        BookAppointmentRequest london = new BookAppointmentRequest(1L, 1L, 1L, 3L,
                OffsetDateTime.of(2026, 10, 5, 9, 0, 0, 0, ZoneOffset.ofHours(1)));
        BookAppointmentRequest sameInstantInUtc = new BookAppointmentRequest(1L, 1L, 1L, 3L,
                OffsetDateTime.of(2026, 10, 5, 8, 0, 0, 0, ZoneOffset.UTC));

        assertThat(sameInstantInUtc.toCommand()).isEqualTo(london.toCommand());
    }

    @Test
    void theStartTimeKeepsOnlyThePrecisionPostgresStores() {
        BookAppointmentRequest request = new BookAppointmentRequest(1L, 1L, 1L, 3L,
                OffsetDateTime.of(2026, 10, 5, 8, 0, 0, 123_456_789, ZoneOffset.UTC));

        assertThat(request.toCommand().startTime().getNano()).isEqualTo(123_456_000);
    }
}
