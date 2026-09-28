package com.keyloop.scheduler.booking.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.keyloop.scheduler.booking.application.port.in.BookAppointmentCommand;
import com.keyloop.scheduler.booking.application.port.in.BookingResult;
import com.keyloop.scheduler.booking.application.port.out.AppointmentRepository;
import com.keyloop.scheduler.booking.application.port.out.BookingMetrics;
import com.keyloop.scheduler.booking.application.port.out.CandidateFinder;
import com.keyloop.scheduler.booking.application.port.out.InsertResult;
import com.keyloop.scheduler.booking.domain.Appointment;
import com.keyloop.scheduler.booking.domain.AppointmentStatus;
import com.keyloop.scheduler.catalog.application.port.out.CatalogRepository;
import com.keyloop.scheduler.catalog.domain.Dealership;
import com.keyloop.scheduler.catalog.domain.ServiceType;
import com.keyloop.scheduler.shared.domain.ConflictException;
import com.keyloop.scheduler.shared.domain.TimeSlot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BookingServiceTest {

    private static final long VEHICLE = 1;
    private static final BookAppointmentCommand COMMAND = new BookAppointmentCommand(1L, 1L, VEHICLE, 3L,
            Instant.parse("2026-10-05T08:00:00Z"));
    private static final TimeSlot SLOT = TimeSlot.of(COMMAND.startTime(), Duration.ofMinutes(90));

    private final CatalogRepository catalog = mock(CatalogRepository.class);
    private final CandidateFinder candidates = mock(CandidateFinder.class);
    private final AppointmentRepository appointments = mock(AppointmentRepository.class);
    private final BookingMetrics metrics = mock(BookingMetrics.class);
    private BookingService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-01T00:00:00Z"), ZoneOffset.UTC);
        service = new BookingService(catalog, candidates, appointments, metrics, new BookingProperties(3), clock);

        when(catalog.findDealership(1)).thenReturn(Optional.of(new Dealership(1, "London",
                ZoneId.of("Europe/London"), LocalTime.of(8, 0), LocalTime.of(18, 0))));
        when(catalog.findServiceType(3)).thenReturn(Optional.of(new ServiceType(3, "WHEEL_ALIGNMENT", "Alignment",
                Duration.ofMinutes(90), "ALIGNMENT", "ALIGNMENT")));
        when(catalog.customerExists(1)).thenReturn(true);
        when(catalog.findVehicleOwner(VEHICLE)).thenReturn(Optional.of(1L));
        when(appointments.findConfirmedForVehicle(eq(VEHICLE), any())).thenReturn(Optional.empty());
    }

    private void candidates(List<Long> bays, List<Long> technicians) {
        when(candidates.freeBays(eq(1L), eq("ALIGNMENT"), any())).thenReturn(bays);
        when(candidates.freeTechnicians(eq(1L), eq("ALIGNMENT"), any())).thenReturn(technicians);
    }

    private List<Appointment> attemptedInserts(int expected) {
        ArgumentCaptor<Appointment> captor = ArgumentCaptor.forClass(Appointment.class);
        verify(appointments, times(expected)).insert(captor.capture());
        return captor.getAllValues();
    }

    private static Appointment sameBooking() {
        return new Appointment(UUID.randomUUID(), 1, 1, VEHICLE, 3, 10, 20, SLOT, AppointmentStatus.CONFIRMED,
                Instant.now(), null);
    }

    private static Appointment otherBookingOfTheVehicle() {
        return new Appointment(UUID.randomUUID(), 1, 1, VEHICLE, 1, 11, 21,
                TimeSlot.of(SLOT.start().plusSeconds(1800), Duration.ofMinutes(60)), AppointmentStatus.CONFIRMED,
                Instant.now(), null);
    }

    private void vehicleFreeAtFirstThenHeldBy(Appointment holder) {
        when(appointments.findConfirmedForVehicle(eq(VEHICLE), any()))
                .thenReturn(Optional.empty(), Optional.ofNullable(holder));
    }

    @Test
    void aLostRaceForTheBayMovesOnToTheOtherBayAndKeepsTheTechnician() {
        candidates(List.of(10L, 11L), List.of(20L));
        when(appointments.insert(any())).thenReturn(InsertResult.BAY_TAKEN, InsertResult.INSERTED);

        BookingResult result = service.book(COMMAND);

        List<Appointment> attempts = attemptedInserts(2);
        assertThat(attempts.get(0).serviceBayId()).isNotEqualTo(attempts.get(1).serviceBayId());
        assertThat(result.appointment().serviceBayId()).isEqualTo(attempts.get(1).serviceBayId());
        assertThat(result.appointment().technicianId()).isEqualTo(20L);
        assertThat(result.appointment().vehicleId()).isEqualTo(VEHICLE);
        assertThat(result.replayed()).isFalse();
        verify(metrics).bayConflict();
        verify(metrics).confirmed();
    }

    @Test
    void aLostRaceForTheTechnicianMovesOnToTheOtherTechnicianAndKeepsTheBay() {
        candidates(List.of(10L), List.of(20L, 21L));
        when(appointments.insert(any())).thenReturn(InsertResult.TECHNICIAN_TAKEN, InsertResult.INSERTED);

        BookingResult result = service.book(COMMAND);

        List<Appointment> attempts = attemptedInserts(2);
        assertThat(attempts.get(0).technicianId()).isNotEqualTo(attempts.get(1).technicianId());
        assertThat(result.appointment().serviceBayId()).isEqualTo(10L);
        verify(metrics).technicianConflict();
    }

    @Test
    void whenEveryCandidateIsTakenTheRequestIsRejectedAsNoCapacity() {
        candidates(List.of(10L), List.of(20L));
        when(appointments.insert(any())).thenReturn(InsertResult.BAY_TAKEN);

        assertThatThrownBy(() -> service.book(COMMAND))
                .isInstanceOfSatisfying(ConflictException.class, e -> {
                    assertThat(e.title()).isEqualTo("No availability");
                    assertThat(e.getMessage()).contains("No ALIGNMENT service bay");
                });
        verify(metrics).noCapacity();
    }

    @Test
    void retriesAreBoundedEvenIfCandidatesRemain() {
        candidates(List.of(10L, 11L, 12L, 13L), List.of(20L));
        when(appointments.insert(any())).thenReturn(InsertResult.BAY_TAKEN);

        assertThatThrownBy(() -> service.book(COMMAND))
                .isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.getMessage()).contains("high demand"));
        attemptedInserts(3);
        verify(metrics).contentionExhausted();
    }

    @Test
    void theSameRequestAlreadyBookedIsReplayedWithoutAllocating() {
        when(appointments.findConfirmedForVehicle(eq(VEHICLE), any())).thenReturn(Optional.of(sameBooking()));

        assertThat(service.book(COMMAND).replayed()).isTrue();
        verify(metrics).replayed();
        verify(candidates, never()).freeBays(anyLong(), anyString(), any());
        verify(appointments, never()).insert(any());
    }

    @Test
    void aVehicleThatAlreadyHoldsAnOverlappingAppointmentIsRejectedWithoutAllocating() {
        when(appointments.findConfirmedForVehicle(eq(VEHICLE), any()))
                .thenReturn(Optional.of(otherBookingOfTheVehicle()));

        assertThatThrownBy(() -> service.book(COMMAND))
                .isInstanceOfSatisfying(ConflictException.class, e -> {
                    assertThat(e.title()).isEqualTo("Vehicle already booked");
                    assertThat(e.getMessage()).contains("Vehicle 1 already has an appointment");
                });
        verify(metrics).vehicleBusy();
        verify(appointments, never()).insert(any());
    }

    @Test
    void theSameRequestArrivingConcurrentlyReturnsTheAppointmentThatWon() {
        candidates(List.of(10L), List.of(20L));
        when(appointments.insert(any())).thenReturn(InsertResult.VEHICLE_TAKEN);
        Appointment winner = sameBooking();
        vehicleFreeAtFirstThenHeldBy(winner);

        BookingResult result = service.book(COMMAND);

        assertThat(result.replayed()).isTrue();
        assertThat(result.appointment()).isSameAs(winner);
    }

    @Test
    void aDifferentConcurrentBookingOfTheVehicleWinsAndThisOneIsRejected() {
        candidates(List.of(10L), List.of(20L));
        when(appointments.insert(any())).thenReturn(InsertResult.VEHICLE_TAKEN);
        vehicleFreeAtFirstThenHeldBy(otherBookingOfTheVehicle());

        assertThatThrownBy(() -> service.book(COMMAND))
                .isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.title()).isEqualTo("Vehicle already booked"));
        verify(metrics).vehicleBusy();
    }

    @Test
    void anOverlapCausedByTheSameRequestInFlightIsAReplayNotAConflict() {
        candidates(List.of(10L), List.of(20L));
        when(appointments.insert(any())).thenReturn(InsertResult.BAY_TAKEN);
        Appointment winner = sameBooking();
        vehicleFreeAtFirstThenHeldBy(winner);

        BookingResult result = service.book(COMMAND);

        assertThat(result.replayed()).isTrue();
        assertThat(result.appointment()).isSameAs(winner);
        verify(metrics, never()).noCapacity();
    }

    @Test
    void whenTheVehiclesHolderIsCancelledMeanwhileTheInsertIsTriedAgain() {
        candidates(List.of(10L), List.of(20L));
        when(appointments.insert(any())).thenReturn(InsertResult.VEHICLE_TAKEN, InsertResult.INSERTED);
        vehicleFreeAtFirstThenHeldBy(null);

        BookingResult result = service.book(COMMAND);

        assertThat(result.replayed()).isFalse();
        attemptedInserts(2);
        verify(metrics).confirmed();
    }
}
