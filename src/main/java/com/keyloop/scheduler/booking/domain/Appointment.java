package com.keyloop.scheduler.booking.domain;

import java.time.Instant;
import java.util.UUID;

import com.keyloop.scheduler.shared.domain.TimeSlot;

public record Appointment(
        UUID id,
        long dealershipId,
        long customerId,
        long vehicleId,
        long serviceTypeId,
        long serviceBayId,
        long technicianId,
        TimeSlot slot,
        AppointmentStatus status,
        Instant createdAt,
        Instant cancelledAt) {

    public boolean isSameBookingAs(long dealershipId, long customerId, long serviceTypeId, Instant start) {
        return this.dealershipId == dealershipId && this.customerId == customerId
                && this.serviceTypeId == serviceTypeId && slot.start().equals(start);
    }
}
