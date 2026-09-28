package com.keyloop.scheduler.booking.adapter.out.persistence;

import static com.keyloop.scheduler.shared.adapter.out.persistence.UtcTimestamps.utc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.keyloop.scheduler.booking.application.port.out.AppointmentRepository;
import com.keyloop.scheduler.booking.application.port.out.InsertResult;
import com.keyloop.scheduler.booking.domain.Appointment;
import com.keyloop.scheduler.shared.domain.TimeSlot;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
class AppointmentPersistenceAdapter implements AppointmentRepository {

    private final AppointmentJpaRepository appointments;

    AppointmentPersistenceAdapter(AppointmentJpaRepository appointments) {
        this.appointments = appointments;
    }

    @Override
    public Optional<Appointment> findById(UUID id) {
        return appointments.findById(id).map(AppointmentJpaEntity::toDomain);
    }

    @Override
    public Optional<Appointment> findConfirmedForVehicle(long vehicleId, TimeSlot slot) {
        return appointments.findConfirmedForVehicle(vehicleId, slot).map(AppointmentJpaEntity::toDomain);
    }

    @Override
    public InsertResult insert(Appointment appointment) {
        try {
            appointments.insert(AppointmentJpaEntity.from(appointment));
            return InsertResult.INSERTED;
        } catch (DataIntegrityViolationException e) {
            String constraint = violatedConstraint(e);
            if (AppointmentJpaRepository.BAY_OVERLAP_CONSTRAINT.equals(constraint)) {
                return InsertResult.BAY_TAKEN;
            }
            if (AppointmentJpaRepository.TECHNICIAN_OVERLAP_CONSTRAINT.equals(constraint)) {
                return InsertResult.TECHNICIAN_TAKEN;
            }
            if (AppointmentJpaRepository.VEHICLE_OVERLAP_CONSTRAINT.equals(constraint)) {
                return InsertResult.VEHICLE_TAKEN;
            }
            throw e;
        }
    }

    @Override
    public Optional<Appointment> cancel(UUID id, Instant now) {
        return appointments.markCancelled(id, utc(now)) == 1 ? findById(id) : Optional.empty();
    }

    @Override
    public List<Appointment> findOverlapping(long dealershipId, TimeSlot window) {
        return appointments.findOverlapping(dealershipId, window).stream()
                .map(AppointmentJpaEntity::toDomain)
                .toList();
    }

    private static String violatedConstraint(DataIntegrityViolationException e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof PSQLException psql) {
                ServerErrorMessage message = psql.getServerErrorMessage();
                return message == null ? null : message.getConstraint();
            }
        }
        return null;
    }
}
