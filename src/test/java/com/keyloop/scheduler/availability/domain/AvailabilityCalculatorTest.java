package com.keyloop.scheduler.availability.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.keyloop.scheduler.availability.domain.AvailabilityCalculator.BayCalendar;
import com.keyloop.scheduler.availability.domain.AvailabilityCalculator.TechnicianCalendar;
import com.keyloop.scheduler.shared.domain.TimeSlot;
import org.junit.jupiter.api.Test;

class AvailabilityCalculatorTest {

    private static final TimeSlot OPENING_HOURS = slot("08:00", "12:00");
    private static final Duration ONE_HOUR = Duration.ofHours(1);
    private static final Duration STEP = Duration.ofMinutes(30);
    private static final Instant EARLY = Instant.parse("2026-10-05T00:00:00Z");

    private static Instant at(String time) {
        return Instant.parse("2026-10-05T" + time + ":00Z");
    }

    private static TimeSlot slot(String start, String end) {
        return new TimeSlot(at(start), at(end));
    }

    private static BayCalendar freeBay(long id) {
        return new BayCalendar(id, List.of());
    }

    private static TechnicianCalendar technician(long id, TimeSlot shift, TimeSlot... busy) {
        return new TechnicianCalendar(id, List.of(shift), List.of(busy));
    }

    private static List<Instant> starts(List<TimeSlot> slots) {
        return slots.stream().map(TimeSlot::start).toList();
    }

    @Test
    void everyStepThatFitsBeforeClosingIsFreeWhenNothingIsBooked() {
        List<TimeSlot> free = AvailabilityCalculator.freeSlots(OPENING_HOURS, ONE_HOUR, STEP, EARLY,
                List.of(freeBay(1)), List.of(technician(1, OPENING_HOURS)));

        assertThat(starts(free)).containsExactly(
                at("08:00"), at("08:30"), at("09:00"), at("09:30"), at("10:00"), at("10:30"), at("11:00"));
    }

    @Test
    void aBusyBayBlocksOverlappingStartsButNotBackToBackOnes() {
        BayCalendar bay = new BayCalendar(1, List.of(slot("09:00", "10:00")));

        List<TimeSlot> free = AvailabilityCalculator.freeSlots(OPENING_HOURS, ONE_HOUR, STEP, EARLY,
                List.of(bay), List.of(technician(1, OPENING_HOURS)));

        assertThat(starts(free)).containsExactly(at("08:00"), at("10:00"), at("10:30"), at("11:00"));
    }

    @Test
    void aSecondBayKeepsTheSlotOpen() {
        BayCalendar busyBay = new BayCalendar(1, List.of(slot("08:00", "12:00")));

        List<TimeSlot> free = AvailabilityCalculator.freeSlots(OPENING_HOURS, ONE_HOUR, STEP, EARLY,
                List.of(busyBay, freeBay(2)), List.of(technician(1, OPENING_HOURS)));

        assertThat(free).hasSize(7);
    }

    @Test
    void theTechnicianMustBeOnShiftForTheWholeSlot() {
        List<TimeSlot> free = AvailabilityCalculator.freeSlots(OPENING_HOURS, ONE_HOUR, STEP, EARLY,
                List.of(freeBay(1)), List.of(technician(1, slot("09:30", "11:30"))));

        assertThat(starts(free)).containsExactly(at("09:30"), at("10:00"), at("10:30"));
    }

    @Test
    void anotherTechnicianCoversWhenTheFirstIsBusy() {
        TechnicianCalendar busy = technician(1, OPENING_HOURS, slot("08:00", "12:00"));
        TechnicianCalendar lateShift = technician(2, slot("10:00", "12:00"));

        List<TimeSlot> free = AvailabilityCalculator.freeSlots(OPENING_HOURS, ONE_HOUR, STEP, EARLY,
                List.of(freeBay(1)), List.of(busy, lateShift));

        assertThat(starts(free)).containsExactly(at("10:00"), at("10:30"), at("11:00"));
    }

    @Test
    void startsThatAreNotInTheFutureAreExcluded() {
        List<TimeSlot> free = AvailabilityCalculator.freeSlots(OPENING_HOURS, ONE_HOUR, STEP, at("09:00"),
                List.of(freeBay(1)), List.of(technician(1, OPENING_HOURS)));

        assertThat(starts(free)).first().isEqualTo(at("09:30"));
    }

    @Test
    void nothingIsFreeWithoutBaysOrTechnicians() {
        assertThat(AvailabilityCalculator.freeSlots(OPENING_HOURS, ONE_HOUR, STEP, EARLY,
                List.of(), List.of(technician(1, OPENING_HOURS)))).isEmpty();
        assertThat(AvailabilityCalculator.freeSlots(OPENING_HOURS, ONE_HOUR, STEP, EARLY,
                List.of(freeBay(1)), List.of())).isEmpty();
    }

    @Test
    void aServiceLongerThanTheOpeningHoursNeverFits() {
        assertThat(AvailabilityCalculator.freeSlots(OPENING_HOURS, Duration.ofHours(5), STEP, EARLY,
                List.of(freeBay(1)), List.of(technician(1, OPENING_HOURS)))).isEmpty();
    }
}
