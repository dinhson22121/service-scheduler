package com.keyloop.scheduler.availability.application.port.out;

import java.util.Collection;

import com.keyloop.scheduler.availability.domain.AvailabilityCalculator.BayCalendar;
import com.keyloop.scheduler.availability.domain.AvailabilityCalculator.TechnicianCalendar;
import com.keyloop.scheduler.shared.domain.TimeSlot;

public interface ResourceCalendars {

    Collection<BayCalendar> bays(long dealershipId, String bayType, TimeSlot window);

    Collection<TechnicianCalendar> technicians(long dealershipId, String skill, TimeSlot window);
}
