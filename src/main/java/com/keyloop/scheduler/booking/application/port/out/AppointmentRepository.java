package com.keyloop.scheduler.booking.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.keyloop.scheduler.booking.domain.Appointment;
import com.keyloop.scheduler.shared.domain.TimeSlot;

public interface AppointmentRepository {

    Optional<Appointment> findById(UUID id);

    Optional<Appointment> findConfirmedForVehicle(long vehicleId, TimeSlot slot);

    InsertResult insert(Appointment appointment);

    Optional<Appointment> cancel(UUID id, Instant now);

    List<Appointment> findOverlapping(long dealershipId, TimeSlot window);
}
