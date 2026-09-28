package com.keyloop.scheduler.booking.adapter.in.web;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.keyloop.scheduler.booking.application.port.in.BookAppointmentUseCase;
import com.keyloop.scheduler.booking.application.port.in.BookingResult;
import com.keyloop.scheduler.booking.application.port.in.ManageAppointmentsUseCase;
import com.keyloop.scheduler.booking.domain.Appointment;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
class AppointmentController {

    static final String REPLAYED = "Idempotent-Replayed";

    private final BookAppointmentUseCase bookAppointment;
    private final ManageAppointmentsUseCase appointments;

    AppointmentController(BookAppointmentUseCase bookAppointment, ManageAppointmentsUseCase appointments) {
        this.bookAppointment = bookAppointment;
        this.appointments = appointments;
    }

    @Operation(summary = "Book a service appointment",
            description = "Assigns a free service bay and a qualified technician for the whole service duration. "
                    + "A vehicle holds at most one appointment at a time, so the vehicle and slot identify a booking: "
                    + "sending the same request again returns the original appointment. If no definitive answer "
                    + "arrives (timeout, 502, 504), the booking may still have been made: send the same request "
                    + "again to learn the outcome.")
    @ApiResponse(responseCode = "201", description = "Booked, or the original booking when the same request is repeated",
            headers = {
                    @Header(name = "Location", description = "URL of the appointment",
                            schema = @Schema(type = "string", format = "uri")),
                    @Header(name = REPLAYED, description = "true when this request had already been booked",
                            schema = @Schema(type = "boolean"))})
    @ApiResponse(responseCode = "404", description = "Unknown dealership, service type, customer or vehicle")
    @ApiResponse(responseCode = "409", description = "No suitable bay or qualified technician is free for the whole "
            + "slot, or the vehicle already has a different appointment that overlaps it")
    @ApiResponse(responseCode = "422", description = "Business rule broken: start not in the future, outside opening "
            + "hours, or vehicle of another customer")
    @PostMapping("/appointments")
    ResponseEntity<AppointmentResponse> book(@Valid @RequestBody BookAppointmentRequest request) {
        BookingResult result = bookAppointment.book(request.toCommand());
        Appointment appointment = result.appointment();
        return ResponseEntity.created(URI.create("/api/v1/appointments/" + appointment.id()))
                .header(REPLAYED, String.valueOf(result.replayed()))
                .body(AppointmentResponse.from(appointment));
    }

    @Operation(summary = "Get an appointment")
    @ApiResponse(responseCode = "200", description = "The appointment")
    @ApiResponse(responseCode = "404", description = "Unknown appointment")
    @GetMapping("/appointments/{id}")
    AppointmentResponse get(@PathVariable UUID id) {
        return AppointmentResponse.from(appointments.get(id));
    }

    @Operation(summary = "Cancel an appointment", description = "Idempotent; frees the bay, technician and vehicle.")
    @ApiResponse(responseCode = "200", description = "Cancelled, now or earlier")
    @ApiResponse(responseCode = "404", description = "Unknown appointment")
    @ApiResponse(responseCode = "409", description = "The appointment has already started")
    @PostMapping("/appointments/{id}/cancel")
    AppointmentResponse cancel(@PathVariable UUID id) {
        return AppointmentResponse.from(appointments.cancel(id));
    }

    @Operation(summary = "List a dealership's appointments for a local calendar day")
    @ApiResponse(responseCode = "200", description = "Confirmed and cancelled appointments, by start time")
    @ApiResponse(responseCode = "404", description = "Unknown dealership")
    @GetMapping("/dealerships/{dealershipId}/appointments")
    List<AppointmentResponse> listForDay(
            @PathVariable long dealershipId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return appointments.listForDay(dealershipId, date).stream()
                .map(AppointmentResponse::from)
                .toList();
    }
}
