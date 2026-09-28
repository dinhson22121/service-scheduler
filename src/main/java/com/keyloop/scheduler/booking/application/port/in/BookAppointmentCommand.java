package com.keyloop.scheduler.booking.application.port.in;

import java.time.Instant;

public record BookAppointmentCommand(
        long dealershipId,
        long customerId,
        long vehicleId,
        long serviceTypeId,
        Instant startTime) {
}
