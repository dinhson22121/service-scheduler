package com.keyloop.scheduler.availability.adapter.in.web;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.keyloop.scheduler.availability.application.port.in.DayAvailability;

public record AvailabilityResponse(
        long dealershipId,
        long serviceTypeId,
        LocalDate date,
        String timezone,
        long durationMinutes,
        List<Slot> slots) {

    public record Slot(Instant startTime, Instant endTime) {
    }

    static AvailabilityResponse from(DayAvailability availability) {
        return new AvailabilityResponse(availability.dealershipId(), availability.serviceTypeId(), availability.date(),
                availability.zone().getId(), availability.duration().toMinutes(),
                availability.slots().stream().map(s -> new Slot(s.start(), s.end())).toList());
    }
}
