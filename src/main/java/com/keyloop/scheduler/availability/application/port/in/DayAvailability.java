package com.keyloop.scheduler.availability.application.port.in;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import com.keyloop.scheduler.shared.domain.TimeSlot;

public record DayAvailability(long dealershipId, long serviceTypeId, LocalDate date, ZoneId zone, Duration duration,
                              List<TimeSlot> slots) {
}
