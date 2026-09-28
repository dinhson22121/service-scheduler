package com.keyloop.scheduler.booking.application;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.keyloop.scheduler.booking.application.port.in.ManageAppointmentsUseCase;
import com.keyloop.scheduler.booking.application.port.out.AppointmentRepository;
import com.keyloop.scheduler.booking.application.port.out.BookingMetrics;
import com.keyloop.scheduler.booking.domain.Appointment;
import com.keyloop.scheduler.booking.domain.AppointmentStatus;
import com.keyloop.scheduler.catalog.application.port.out.CatalogRepository;
import com.keyloop.scheduler.catalog.domain.Dealership;
import com.keyloop.scheduler.shared.domain.ConflictException;
import com.keyloop.scheduler.shared.domain.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
class AppointmentService implements ManageAppointmentsUseCase {

    private static final Logger log = LoggerFactory.getLogger(AppointmentService.class);

    private final AppointmentRepository appointments;
    private final CatalogRepository catalog;
    private final BookingMetrics metrics;
    private final Clock clock;

    AppointmentService(AppointmentRepository appointments, CatalogRepository catalog, BookingMetrics metrics,
                       Clock clock) {
        this.appointments = appointments;
        this.catalog = catalog;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Override
    public Appointment get(UUID id) {
        return appointments.findById(id)
                .orElseThrow(() -> new NotFoundException("Appointment " + id + " does not exist"));
    }

    @Override
    public List<Appointment> listForDay(long dealershipId, LocalDate date) {
        Dealership dealership = catalog.findDealership(dealershipId)
                .orElseThrow(() -> new NotFoundException("Dealership " + dealershipId + " does not exist"));
        return appointments.findOverlapping(dealershipId, dealership.localDay(date));
    }

    @Override
    public Appointment cancel(UUID id) {
        var cancelled = appointments.cancel(id, clock.instant());
        if (cancelled.isPresent()) {
            metrics.cancelled();
            log.info("Appointment {} cancelled", id);
            return cancelled.get();
        }
        Appointment current = get(id);
        if (current.status() == AppointmentStatus.CANCELLED) {
            return current;
        }
        throw new ConflictException("Appointment already started", "Appointment " + id + " has already started");
    }
}
