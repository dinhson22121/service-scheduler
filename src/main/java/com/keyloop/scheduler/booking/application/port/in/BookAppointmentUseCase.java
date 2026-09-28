package com.keyloop.scheduler.booking.application.port.in;

public interface BookAppointmentUseCase {

    BookingResult book(BookAppointmentCommand command);
}
