package com.keyloop.scheduler.availability.adapter.in.web;

import java.time.LocalDate;

import com.keyloop.scheduler.availability.application.port.in.FindAvailabilityUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AvailabilityController {

    private final FindAvailabilityUseCase findAvailability;

    AvailabilityController(FindAvailabilityUseCase findAvailability) {
        this.findAvailability = findAvailability;
    }

    @Operation(summary = "Free start times for a service on a local calendar day",
            description = "Advisory: booking re-checks availability atomically.")
    @ApiResponse(responseCode = "200", description = "Free start times; empty when nothing fits")
    @ApiResponse(responseCode = "404", description = "Unknown dealership or service type")
    @GetMapping("/api/v1/dealerships/{dealershipId}/availability")
    AvailabilityResponse availability(
            @PathVariable long dealershipId,
            @RequestParam long serviceTypeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return AvailabilityResponse.from(findAvailability.availability(dealershipId, serviceTypeId, date));
    }
}
