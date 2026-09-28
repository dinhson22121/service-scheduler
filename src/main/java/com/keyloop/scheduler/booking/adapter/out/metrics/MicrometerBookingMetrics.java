package com.keyloop.scheduler.booking.adapter.out.metrics;

import com.keyloop.scheduler.booking.application.port.out.BookingMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
class MicrometerBookingMetrics implements BookingMetrics {

    private final Counter confirmed;
    private final Counter replayed;
    private final Counter noCapacity;
    private final Counter vehicleBusy;
    private final Counter contentionExhausted;
    private final Counter bayConflicts;
    private final Counter technicianConflicts;
    private final Counter cancelled;

    MicrometerBookingMetrics(MeterRegistry registry) {
        confirmed = booking(registry, "confirmed");
        replayed = booking(registry, "replayed");
        noCapacity = booking(registry, "no_capacity");
        vehicleBusy = booking(registry, "vehicle_busy");
        contentionExhausted = booking(registry, "contention_exhausted");
        bayConflicts = conflict(registry, "bay");
        technicianConflicts = conflict(registry, "technician");
        cancelled = Counter.builder("scheduler.cancellations")
                .description("Appointments cancelled")
                .register(registry);
    }

    @Override
    public void confirmed() {
        confirmed.increment();
    }

    @Override
    public void replayed() {
        replayed.increment();
    }

    @Override
    public void noCapacity() {
        noCapacity.increment();
    }

    @Override
    public void vehicleBusy() {
        vehicleBusy.increment();
    }

    @Override
    public void contentionExhausted() {
        contentionExhausted.increment();
    }

    @Override
    public void bayConflict() {
        bayConflicts.increment();
    }

    @Override
    public void technicianConflict() {
        technicianConflicts.increment();
    }

    @Override
    public void cancelled() {
        cancelled.increment();
    }

    private static Counter booking(MeterRegistry registry, String outcome) {
        return Counter.builder("scheduler.bookings")
                .description("Booking requests by outcome")
                .tag("outcome", outcome)
                .register(registry);
    }

    private static Counter conflict(MeterRegistry registry, String resource) {
        return Counter.builder("scheduler.booking.conflicts")
                .description("Inserts rejected by an exclusion constraint because a concurrent request won the resource")
                .tag("resource", resource)
                .register(registry);
    }
}
