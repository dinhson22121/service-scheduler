package com.keyloop.scheduler.booking.adapter.in.web;

import java.time.Instant;
import java.util.UUID;

import com.keyloop.scheduler.booking.domain.Appointment;
import com.keyloop.scheduler.booking.domain.AppointmentStatus;

public record AppointmentResponse(
        UUID id,
        AppointmentStatus status,
        long dealershipId,
        long customerId,
        long vehicleId,
        long serviceTypeId,
        long serviceBayId,
        long technicianId,
        Instant startTime,
        Instant endTime,
        Instant createdAt,
        Instant cancelledAt) {

    static AppointmentResponse from(Appointment a) {
        return new AppointmentResponse(a.id(), a.status(), a.dealershipId(), a.customerId(), a.vehicleId(),
                a.serviceTypeId(), a.serviceBayId(), a.technicianId(), a.slot().start(), a.slot().end(),
                a.createdAt(), a.cancelledAt());
    }
}
