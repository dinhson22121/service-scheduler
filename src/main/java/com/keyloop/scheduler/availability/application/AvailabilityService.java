package com.keyloop.scheduler.availability.application;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import com.keyloop.scheduler.availability.application.port.in.DayAvailability;
import com.keyloop.scheduler.availability.application.port.in.FindAvailabilityUseCase;
import com.keyloop.scheduler.availability.application.port.out.ResourceCalendars;
import com.keyloop.scheduler.availability.domain.AvailabilityCalculator;
import com.keyloop.scheduler.catalog.application.port.out.CatalogRepository;
import com.keyloop.scheduler.catalog.domain.Dealership;
import com.keyloop.scheduler.catalog.domain.ServiceType;
import com.keyloop.scheduler.shared.domain.NotFoundException;
import com.keyloop.scheduler.shared.domain.TimeSlot;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.stereotype.Service;

@Service
class AvailabilityService implements FindAvailabilityUseCase {

    @ConfigurationProperties("scheduler.availability")
    record AvailabilityProperties(@DefaultValue("15") int stepMinutes) {
    }

    private final CatalogRepository catalog;
    private final ResourceCalendars calendars;
    private final AvailabilityProperties properties;
    private final Clock clock;

    AvailabilityService(CatalogRepository catalog, ResourceCalendars calendars, AvailabilityProperties properties,
                        Clock clock) {
        this.catalog = catalog;
        this.calendars = calendars;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public DayAvailability availability(long dealershipId, long serviceTypeId, LocalDate date) {
        Dealership dealership = catalog.findDealership(dealershipId)
                .orElseThrow(() -> new NotFoundException("Dealership " + dealershipId + " does not exist"));
        ServiceType serviceType = catalog.findServiceType(serviceTypeId)
                .orElseThrow(() -> new NotFoundException("Service type " + serviceTypeId + " does not exist"));

        TimeSlot openingHours = dealership.openingHours(date);
        List<TimeSlot> free = AvailabilityCalculator.freeSlots(
                openingHours,
                serviceType.duration(),
                Duration.ofMinutes(properties.stepMinutes()),
                clock.instant(),
                calendars.bays(dealershipId, serviceType.requiredBayType(), openingHours),
                calendars.technicians(dealershipId, serviceType.requiredSkill(), openingHours));

        return new DayAvailability(dealershipId, serviceTypeId, date, dealership.zone(), serviceType.duration(), free);
    }
}
