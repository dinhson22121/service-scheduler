package com.keyloop.scheduler.availability.application.port.in;

import java.time.LocalDate;

public interface FindAvailabilityUseCase {

    DayAvailability availability(long dealershipId, long serviceTypeId, LocalDate date);
}
