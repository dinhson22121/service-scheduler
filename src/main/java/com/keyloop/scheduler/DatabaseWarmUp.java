package com.keyloop.scheduler;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import com.keyloop.scheduler.availability.application.port.out.ResourceCalendars;
import com.keyloop.scheduler.booking.adapter.out.persistence.AppointmentJpaRepository;
import com.keyloop.scheduler.booking.application.port.out.AppointmentRepository;
import com.keyloop.scheduler.booking.application.port.out.CandidateFinder;
import com.keyloop.scheduler.catalog.application.port.out.CatalogRepository;
import com.keyloop.scheduler.shared.domain.TimeSlot;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
class DatabaseWarmUp implements ApplicationRunner {

    private static final long NO_ID = 0;
    private static final String NO_CODE = "";

    private final CatalogRepository catalog;
    private final CandidateFinder candidates;
    private final AppointmentRepository appointments;
    private final ResourceCalendars calendars;
    private final AppointmentJpaRepository appointmentRows;
    private final Clock clock;

    DatabaseWarmUp(CatalogRepository catalog, CandidateFinder candidates, AppointmentRepository appointments,
                   ResourceCalendars calendars, AppointmentJpaRepository appointmentRows, Clock clock) {
        this.catalog = catalog;
        this.candidates = candidates;
        this.appointments = appointments;
        this.calendars = calendars;
        this.appointmentRows = appointmentRows;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        TimeSlot slot = TimeSlot.of(clock.instant(), Duration.ofHours(1));
        catalog.findDealership(NO_ID);
        catalog.findServiceType(NO_ID);
        catalog.customerExists(NO_ID);
        catalog.findVehicleOwner(NO_ID);
        candidates.freeBays(NO_ID, NO_CODE, slot);
        candidates.freeTechnicians(NO_ID, NO_CODE, slot);
        appointments.findById(new UUID(0, 0));
        appointments.findConfirmedForVehicle(NO_ID, slot);
        appointments.findOverlapping(NO_ID, slot);
        calendars.bays(NO_ID, NO_CODE, slot);
        calendars.technicians(NO_ID, NO_CODE, slot);
        appointmentRows.findConfirmedForTechnicians(List.of(NO_ID), slot);
    }
}
