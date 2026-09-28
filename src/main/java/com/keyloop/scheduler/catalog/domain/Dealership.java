package com.keyloop.scheduler.catalog.domain;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import com.keyloop.scheduler.shared.domain.TimeSlot;

public record Dealership(long id, String name, ZoneId zone, LocalTime opensAt, LocalTime closesAt) {

    public TimeSlot openingHours(LocalDate localDate) {
        return new TimeSlot(
                localDate.atTime(opensAt).atZone(zone).toInstant(),
                localDate.atTime(closesAt).atZone(zone).toInstant());
    }

    public TimeSlot localDay(LocalDate localDate) {
        return new TimeSlot(
                localDate.atStartOfDay(zone).toInstant(),
                localDate.plusDays(1).atStartOfDay(zone).toInstant());
    }

    public boolean isOpenFor(TimeSlot slot) {
        LocalDate localDate = slot.start().atZone(zone).toLocalDate();
        return openingHours(localDate).contains(slot);
    }
}
