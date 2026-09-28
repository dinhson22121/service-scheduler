package com.keyloop.scheduler.booking.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.keyloop.scheduler.booking.application.port.out.InsertResult;
import com.keyloop.scheduler.booking.domain.Appointment;
import com.keyloop.scheduler.booking.domain.AppointmentStatus;
import com.keyloop.scheduler.shared.domain.TimeSlot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataIntegrityViolationException;

class AppointmentPersistenceAdapterTest {

    private static final Appointment APPOINTMENT = new Appointment(UUID.randomUUID(), 1, 1, 1, 3, 10, 20,
            TimeSlot.of(Instant.parse("2026-10-05T08:00:00Z"), Duration.ofMinutes(90)), AppointmentStatus.CONFIRMED,
            Instant.parse("2026-10-01T00:00:00Z"), null);

    private final AppointmentJpaRepository jpa = mock(AppointmentJpaRepository.class);
    private final AppointmentPersistenceAdapter adapter = new AppointmentPersistenceAdapter(jpa);

    private static DataIntegrityViolationException violation(String constraint) {
        ServerErrorMessage message = new ServerErrorMessage(
                "SERROR\0C23P01\0Mconflicting key value violates exclusion constraint\0n" + constraint + "\0");
        return new DataIntegrityViolationException("insert failed", new PSQLException(message));
    }

    @Test
    void aSuccessfulInsertIsReportedAsInserted() {
        assertThat(adapter.insert(APPOINTMENT)).isEqualTo(InsertResult.INSERTED);
    }

    @ParameterizedTest
    @CsvSource({
            AppointmentJpaRepository.BAY_OVERLAP_CONSTRAINT + ", BAY_TAKEN",
            AppointmentJpaRepository.TECHNICIAN_OVERLAP_CONSTRAINT + ", TECHNICIAN_TAKEN",
            AppointmentJpaRepository.VEHICLE_OVERLAP_CONSTRAINT + ", VEHICLE_TAKEN"})
    void theViolatedConstraintSaysWhichResourceWasLost(String constraint, InsertResult expected) {
        doThrow(violation(constraint)).when(jpa).insert(any());

        assertThat(adapter.insert(APPOINTMENT)).isEqualTo(expected);
    }

    @Test
    void anUnexpectedIntegrityViolationIsNotSwallowed() {
        DataIntegrityViolationException fkViolation = violation("appointment_vehicle_fk");
        doThrow(fkViolation).when(jpa).insert(any());

        assertThatThrownBy(() -> adapter.insert(APPOINTMENT)).isSameAs(fkViolation);
    }

    @Test
    void theDomainAppointmentSurvivesTheRoundTripThroughTheEntity() {
        assertThat(AppointmentJpaEntity.from(APPOINTMENT).toDomain()).isEqualTo(APPOINTMENT);
    }
}
