package com.keyloop.scheduler.shared.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record TimeSlot(Instant start, Instant end) {

    public TimeSlot {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("start must be before end");
        }
    }

    public static TimeSlot of(Instant start, Duration duration) {
        return new TimeSlot(start, start.plus(duration));
    }

    public boolean overlaps(TimeSlot other) {
        return start.isBefore(other.end) && other.start.isBefore(end);
    }

    public boolean contains(TimeSlot other) {
        return !other.start.isBefore(start) && !other.end.isAfter(end);
    }
}
