package com.keyloop.scheduler.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import com.keyloop.scheduler.shared.domain.TimeSlot;
import org.junit.jupiter.api.Test;

class DealershipTest {

    private final Dealership london = new Dealership(1, "London", ZoneId.of("Europe/London"),
            LocalTime.of(8, 0), LocalTime.of(18, 0));
    private final Dealership saigon = new Dealership(2, "Saigon", ZoneId.of("Asia/Ho_Chi_Minh"),
            LocalTime.of(7, 30), LocalTime.of(17, 30));

    @Test
    void openingHoursFollowDaylightSavingTime() {
        assertThat(london.openingHours(LocalDate.of(2026, 10, 23)).start())
                .isEqualTo(Instant.parse("2026-10-23T07:00:00Z"));
        assertThat(london.openingHours(LocalDate.of(2026, 10, 26)).start())
                .isEqualTo(Instant.parse("2026-10-26T08:00:00Z"));
    }

    @Test
    void openingHoursUseTheDealershipsOwnZone() {
        assertThat(saigon.openingHours(LocalDate.of(2026, 10, 5)))
                .isEqualTo(new TimeSlot(Instant.parse("2026-10-05T00:30:00Z"), Instant.parse("2026-10-05T10:30:00Z")));
    }

    @Test
    void acceptsAServiceThatEndsExactlyAtClosingTime() {
        Instant start = Instant.parse("2026-10-05T15:30:00Z");
        assertThat(london.isOpenFor(TimeSlot.of(start, Duration.ofMinutes(90)))).isTrue();
    }

    @Test
    void rejectsAServiceThatRunsPastClosingOrStartsBeforeOpening() {
        assertThat(london.isOpenFor(TimeSlot.of(Instant.parse("2026-10-05T15:31:00Z"), Duration.ofMinutes(90))))
                .isFalse();
        assertThat(london.isOpenFor(TimeSlot.of(Instant.parse("2026-10-05T06:59:00Z"), Duration.ofMinutes(60))))
                .isFalse();
    }

    @Test
    void localDayCoversTheWholeCalendarDayInTheDealershipZone() {
        assertThat(saigon.localDay(LocalDate.of(2026, 10, 5)))
                .isEqualTo(new TimeSlot(Instant.parse("2026-10-04T17:00:00Z"), Instant.parse("2026-10-05T17:00:00Z")));
    }
}
