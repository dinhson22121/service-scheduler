package com.keyloop.scheduler.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class TimeSlotTest {

    private static final Instant NINE = Instant.parse("2026-10-05T09:00:00Z");

    private static TimeSlot slot(String start, String end) {
        return new TimeSlot(Instant.parse("2026-10-05T" + start + ":00Z"), Instant.parse("2026-10-05T" + end + ":00Z"));
    }

    @Test
    void ofAddsTheDurationToTheStart() {
        assertThat(TimeSlot.of(NINE, Duration.ofMinutes(90)))
                .isEqualTo(new TimeSlot(NINE, Instant.parse("2026-10-05T10:30:00Z")));
    }

    @Test
    void backToBackSlotsDoNotOverlap() {
        assertThat(slot("09:00", "10:00").overlaps(slot("10:00", "11:00"))).isFalse();
        assertThat(slot("10:00", "11:00").overlaps(slot("09:00", "10:00"))).isFalse();
    }

    @Test
    void partiallyOrFullyCoveringSlotsOverlap() {
        assertThat(slot("09:00", "10:00").overlaps(slot("09:59", "11:00"))).isTrue();
        assertThat(slot("09:00", "12:00").overlaps(slot("10:00", "11:00"))).isTrue();
        assertThat(slot("10:00", "11:00").overlaps(slot("09:00", "12:00"))).isTrue();
    }

    @Test
    void containsIncludesTheEdges() {
        TimeSlot shift = slot("08:00", "16:00");
        assertThat(shift.contains(slot("08:00", "16:00"))).isTrue();
        assertThat(shift.contains(slot("14:30", "16:00"))).isTrue();
        assertThat(shift.contains(slot("15:00", "16:01"))).isFalse();
        assertThat(shift.contains(slot("07:59", "09:00"))).isFalse();
    }

    @Test
    void rejectsEmptyOrInvertedSlots() {
        assertThatThrownBy(() -> new TimeSlot(NINE, NINE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TimeSlot(NINE, NINE.minusSeconds(1))).isInstanceOf(IllegalArgumentException.class);
    }
}
