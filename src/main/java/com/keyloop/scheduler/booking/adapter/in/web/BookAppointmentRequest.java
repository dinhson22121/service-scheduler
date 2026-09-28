package com.keyloop.scheduler.booking.adapter.in.web;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

import com.keyloop.scheduler.booking.application.port.in.BookAppointmentCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record BookAppointmentRequest(
        @NotNull @Positive Long dealershipId,
        @NotNull @Positive Long customerId,
        @NotNull @Positive Long vehicleId,
        @NotNull @Positive Long serviceTypeId,
        @Schema(description = "Desired start, ISO-8601 with offset", example = "2026-10-05T09:00:00+01:00")
        @NotNull OffsetDateTime startTime) {

    BookAppointmentCommand toCommand() {
        return new BookAppointmentCommand(dealershipId, customerId, vehicleId, serviceTypeId,
                startTime.toInstant().truncatedTo(ChronoUnit.MICROS));
    }
}
