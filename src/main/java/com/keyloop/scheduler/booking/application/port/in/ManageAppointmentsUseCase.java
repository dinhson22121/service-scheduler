package com.keyloop.scheduler.booking.application.port.in;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.keyloop.scheduler.booking.domain.Appointment;

public interface ManageAppointmentsUseCase {

    Appointment get(UUID id);

    Appointment cancel(UUID id);

    List<Appointment> listForDay(long dealershipId, LocalDate date);
}
