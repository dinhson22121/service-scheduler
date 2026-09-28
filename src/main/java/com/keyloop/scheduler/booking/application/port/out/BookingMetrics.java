package com.keyloop.scheduler.booking.application.port.out;

public interface BookingMetrics {

    void confirmed();

    void replayed();

    void noCapacity();

    void vehicleBusy();

    void contentionExhausted();

    void bayConflict();

    void technicianConflict();

    void cancelled();
}
