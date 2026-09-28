package com.keyloop.scheduler.booking.application.port.in;

import com.keyloop.scheduler.booking.domain.Appointment;

public record BookingResult(Appointment appointment, boolean replayed) {
}
