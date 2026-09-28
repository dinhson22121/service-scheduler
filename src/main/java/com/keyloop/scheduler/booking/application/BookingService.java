package com.keyloop.scheduler.booking.application;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

import com.keyloop.scheduler.booking.application.port.in.BookAppointmentCommand;
import com.keyloop.scheduler.booking.application.port.in.BookAppointmentUseCase;
import com.keyloop.scheduler.booking.application.port.in.BookingResult;
import com.keyloop.scheduler.booking.application.port.out.AppointmentRepository;
import com.keyloop.scheduler.booking.application.port.out.BookingMetrics;
import com.keyloop.scheduler.booking.application.port.out.CandidateFinder;
import com.keyloop.scheduler.booking.domain.Appointment;
import com.keyloop.scheduler.booking.domain.AppointmentStatus;
import com.keyloop.scheduler.catalog.application.port.out.CatalogRepository;
import com.keyloop.scheduler.catalog.domain.Dealership;
import com.keyloop.scheduler.catalog.domain.ServiceType;
import com.keyloop.scheduler.shared.domain.BusinessRuleViolationException;
import com.keyloop.scheduler.shared.domain.ConflictException;
import com.keyloop.scheduler.shared.domain.NotFoundException;
import com.keyloop.scheduler.shared.domain.TimeSlot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
class BookingService implements BookAppointmentUseCase {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final CatalogRepository catalog;
    private final CandidateFinder candidates;
    private final AppointmentRepository appointments;
    private final BookingMetrics metrics;
    private final BookingProperties properties;
    private final Clock clock;

    BookingService(CatalogRepository catalog, CandidateFinder candidates, AppointmentRepository appointments,
                   BookingMetrics metrics, BookingProperties properties, Clock clock) {
        this.catalog = catalog;
        this.candidates = candidates;
        this.appointments = appointments;
        this.metrics = metrics;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public BookingResult book(BookAppointmentCommand command) {
        Dealership dealership = catalog.findDealership(command.dealershipId())
                .orElseThrow(() -> new NotFoundException("Dealership " + command.dealershipId() + " does not exist"));
        ServiceType serviceType = catalog.findServiceType(command.serviceTypeId())
                .orElseThrow(() -> new NotFoundException(
                        "Service type " + command.serviceTypeId() + " does not exist"));
        checkCustomerOwnsVehicle(command.customerId(), command.vehicleId());

        TimeSlot slot = TimeSlot.of(command.startTime(), serviceType.duration());
        var holder = appointments.findConfirmedForVehicle(command.vehicleId(), slot);
        if (holder.isPresent()) {
            return replayOrReject(holder.get(), command);
        }
        if (!slot.start().isAfter(clock.instant())) {
            throw new BusinessRuleViolationException("startTime must be in the future");
        }
        if (!dealership.isOpenFor(slot)) {
            throw new BusinessRuleViolationException(
                    "The %d-minute %s must start and finish within opening hours %s-%s (%s)".formatted(
                            serviceType.duration().toMinutes(), serviceType.code(),
                            dealership.opensAt(), dealership.closesAt(), dealership.zone()));
        }
        return allocate(command, serviceType, slot);
    }

    private BookingResult allocate(BookAppointmentCommand command, ServiceType serviceType, TimeSlot slot) {
        Deque<Long> bays = shuffled(candidates.freeBays(command.dealershipId(), serviceType.requiredBayType(), slot));
        Deque<Long> technicians = shuffled(
                candidates.freeTechnicians(command.dealershipId(), serviceType.requiredSkill(), slot));

        int attempt = 0;
        while (attempt < properties.maxAttempts() && !bays.isEmpty() && !technicians.isEmpty()) {
            attempt++;
            Appointment appointment = new Appointment(UUID.randomUUID(), command.dealershipId(),
                    command.customerId(), command.vehicleId(), serviceType.id(), bays.peek(), technicians.peek(),
                    slot, AppointmentStatus.CONFIRMED, now(), null);
            switch (appointments.insert(appointment)) {
                case INSERTED -> {
                    metrics.confirmed();
                    log.info("Appointment {} confirmed: dealership={} bay={} technician={} start={} attempt={}",
                            appointment.id(), appointment.dealershipId(), appointment.serviceBayId(),
                            appointment.technicianId(), slot.start(), attempt);
                    return new BookingResult(appointment, false);
                }
                case BAY_TAKEN -> {
                    metrics.bayConflict();
                    bays.pop();
                }
                case TECHNICIAN_TAKEN -> {
                    metrics.technicianConflict();
                    technicians.pop();
                }
                case VEHICLE_TAKEN -> {
                    var holder = appointments.findConfirmedForVehicle(command.vehicleId(), slot);
                    if (holder.isPresent()) {
                        return replayOrReject(holder.get(), command);
                    }
                }
            }
        }
        var holder = appointments.findConfirmedForVehicle(command.vehicleId(), slot);
        if (holder.isPresent()) {
            return replayOrReject(holder.get(), command);
        }
        throw rejection(bays.isEmpty() || technicians.isEmpty(), bays.isEmpty(), serviceType, slot);
    }

    private RuntimeException rejection(boolean candidatesExhausted, boolean noBay, ServiceType serviceType,
                                       TimeSlot slot) {
        if (candidatesExhausted) {
            metrics.noCapacity();
            return noCapacity(noBay, serviceType);
        }
        metrics.contentionExhausted();
        log.warn("Gave up after {} attempts: serviceType={} start={}",
                properties.maxAttempts(), serviceType.code(), slot.start());
        return new ConflictException("No availability",
                "The slot is in high demand and every candidate was taken concurrently; choose another time");
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private BookingResult replayOrReject(Appointment holder, BookAppointmentCommand command) {
        if (holder.isSameBookingAs(command.dealershipId(), command.customerId(), command.serviceTypeId(),
                command.startTime())) {
            metrics.replayed();
            return new BookingResult(holder, true);
        }
        metrics.vehicleBusy();
        throw new ConflictException("Vehicle already booked",
                "Vehicle %d already has an appointment from %s to %s".formatted(
                        holder.vehicleId(), holder.slot().start(), holder.slot().end()));
    }

    private void checkCustomerOwnsVehicle(long customerId, long vehicleId) {
        if (!catalog.customerExists(customerId)) {
            throw new NotFoundException("Customer " + customerId + " does not exist");
        }
        long owner = catalog.findVehicleOwner(vehicleId)
                .orElseThrow(() -> new NotFoundException("Vehicle " + vehicleId + " does not exist"));
        if (owner != customerId) {
            throw new BusinessRuleViolationException(
                    "Vehicle " + vehicleId + " does not belong to customer " + customerId);
        }
    }

    private static ConflictException noCapacity(boolean noBay, ServiceType serviceType) {
        String detail = noBay
                ? "No %s service bay is free for the whole slot".formatted(serviceType.requiredBayType())
                : "No technician qualified for %s is on shift and free for the whole slot"
                        .formatted(serviceType.requiredSkill());
        return new ConflictException("No availability", detail);
    }

    private static Deque<Long> shuffled(List<Long> ids) {
        List<Long> copy = new ArrayList<>(ids);
        Collections.shuffle(copy);
        return new ArrayDeque<>(copy);
    }
}
