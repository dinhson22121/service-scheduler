package com.keyloop.scheduler.availability.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.keyloop.scheduler.shared.domain.TimeSlot;

public final class AvailabilityCalculator {

    private AvailabilityCalculator() {
    }

    public record BayCalendar(long bayId, List<TimeSlot> busy) {
    }

    public record TechnicianCalendar(long technicianId, List<TimeSlot> shifts, List<TimeSlot> busy) {
    }

    public static List<TimeSlot> freeSlots(TimeSlot openingHours, Duration duration, Duration step, Instant now,
                                    Collection<BayCalendar> bays, Collection<TechnicianCalendar> technicians) {
        List<TimeSlot> free = new ArrayList<>();
        for (Instant start = openingHours.start();
             !start.plus(duration).isAfter(openingHours.end());
             start = start.plus(step)) {
            TimeSlot slot = TimeSlot.of(start, duration);
            if (start.isAfter(now) && anyBayFree(bays, slot) && anyTechnicianFree(technicians, slot)) {
                free.add(slot);
            }
        }
        return free;
    }

    private static boolean anyBayFree(Collection<BayCalendar> bays, TimeSlot slot) {
        return bays.stream().anyMatch(bay -> isFree(bay.busy(), slot));
    }

    private static boolean anyTechnicianFree(Collection<TechnicianCalendar> technicians, TimeSlot slot) {
        return technicians.stream().anyMatch(t ->
                t.shifts().stream().anyMatch(shift -> shift.contains(slot)) && isFree(t.busy(), slot));
    }

    private static boolean isFree(List<TimeSlot> busy, TimeSlot slot) {
        return busy.stream().noneMatch(slot::overlaps);
    }
}
