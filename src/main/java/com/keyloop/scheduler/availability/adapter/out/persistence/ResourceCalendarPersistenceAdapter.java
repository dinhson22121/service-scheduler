package com.keyloop.scheduler.availability.adapter.out.persistence;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.keyloop.scheduler.availability.application.port.out.ResourceCalendars;
import com.keyloop.scheduler.availability.domain.AvailabilityCalculator.BayCalendar;
import com.keyloop.scheduler.availability.domain.AvailabilityCalculator.TechnicianCalendar;
import com.keyloop.scheduler.booking.adapter.out.persistence.AppointmentJpaEntity;
import com.keyloop.scheduler.booking.adapter.out.persistence.AppointmentJpaRepository;
import com.keyloop.scheduler.catalog.adapter.out.persistence.ServiceBayJpaRepository;
import com.keyloop.scheduler.catalog.adapter.out.persistence.TechnicianShiftJpaEntity;
import com.keyloop.scheduler.catalog.adapter.out.persistence.TechnicianShiftJpaRepository;
import com.keyloop.scheduler.shared.domain.TimeSlot;
import org.springframework.stereotype.Component;

@Component
class ResourceCalendarPersistenceAdapter implements ResourceCalendars {

    private final ServiceBayJpaRepository bays;
    private final TechnicianShiftJpaRepository shifts;
    private final AppointmentJpaRepository appointments;

    ResourceCalendarPersistenceAdapter(ServiceBayJpaRepository bays, TechnicianShiftJpaRepository shifts,
                                       AppointmentJpaRepository appointments) {
        this.bays = bays;
        this.shifts = shifts;
        this.appointments = appointments;
    }

    @Override
    public Collection<BayCalendar> bays(long dealershipId, String bayType, TimeSlot window) {
        Map<Long, List<TimeSlot>> busy = appointments.findConfirmedAtBays(dealershipId, bayType, window).stream()
                .collect(groupingBy(AppointmentJpaEntity::serviceBayId, mapping(AppointmentJpaEntity::slot, toList())));
        return bays.findIds(dealershipId, bayType).stream()
                .map(id -> new BayCalendar(id, busy.getOrDefault(id, List.of())))
                .toList();
    }

    @Override
    public Collection<TechnicianCalendar> technicians(long dealershipId, String skill, TimeSlot window) {
        Map<Long, List<TimeSlot>> shiftsByTechnician = shifts.findQualified(dealershipId, skill, window).stream()
                .collect(groupingBy(TechnicianShiftJpaEntity::technicianId, LinkedHashMap::new,
                        mapping(TechnicianShiftJpaEntity::slot, toList())));
        if (shiftsByTechnician.isEmpty()) {
            return List.of();
        }
        Map<Long, List<TimeSlot>> busy = appointments
                .findConfirmedForTechnicians(shiftsByTechnician.keySet(), window).stream()
                .collect(groupingBy(AppointmentJpaEntity::technicianId,
                        mapping(AppointmentJpaEntity::slot, toList())));
        return shiftsByTechnician.entrySet().stream()
                .map(e -> new TechnicianCalendar(e.getKey(), e.getValue(), busy.getOrDefault(e.getKey(), List.of())))
                .toList();
    }
}
