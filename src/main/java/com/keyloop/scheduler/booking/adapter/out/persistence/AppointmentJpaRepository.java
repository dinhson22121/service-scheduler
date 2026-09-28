package com.keyloop.scheduler.booking.adapter.out.persistence;

import static com.keyloop.scheduler.shared.adapter.out.persistence.UtcTimestamps.utc;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.keyloop.scheduler.shared.domain.TimeSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface AppointmentJpaRepository extends JpaRepository<AppointmentJpaEntity, UUID>, AppointmentInserter {

    String BAY_OVERLAP_CONSTRAINT = "appointment_no_bay_overlap";
    String TECHNICIAN_OVERLAP_CONSTRAINT = "appointment_no_technician_overlap";
    String VEHICLE_OVERLAP_CONSTRAINT = "appointment_no_vehicle_overlap";

    @Query(nativeQuery = true, value = """
            SELECT * FROM appointment
             WHERE vehicle_id = :vehicleId
               AND status = 'CONFIRMED'
               AND slot && tstzrange(:start, :end, '[)')
             LIMIT 1""")
    Optional<AppointmentJpaEntity> findConfirmedForVehicle(long vehicleId, OffsetDateTime start, OffsetDateTime end);

    default Optional<AppointmentJpaEntity> findConfirmedForVehicle(long vehicleId, TimeSlot slot) {
        return findConfirmedForVehicle(vehicleId, utc(slot.start()), utc(slot.end()));
    }

    @Query(nativeQuery = true, value = """
            SELECT * FROM appointment
             WHERE dealership_id = :dealershipId
               AND slot && tstzrange(:start, :end, '[)')
             ORDER BY starts_at, id""")
    List<AppointmentJpaEntity> findOverlapping(long dealershipId, OffsetDateTime start, OffsetDateTime end);

    default List<AppointmentJpaEntity> findOverlapping(long dealershipId, TimeSlot window) {
        return findOverlapping(dealershipId, utc(window.start()), utc(window.end()));
    }

    @Query(nativeQuery = true, value = """
            SELECT a.* FROM appointment a
              JOIN service_bay b ON b.id = a.service_bay_id
             WHERE b.dealership_id = :dealershipId
               AND b.bay_type = :bayType
               AND a.status = 'CONFIRMED'
               AND a.slot && tstzrange(:start, :end, '[)')""")
    List<AppointmentJpaEntity> findConfirmedAtBays(long dealershipId, String bayType, OffsetDateTime start,
                                                   OffsetDateTime end);

    default List<AppointmentJpaEntity> findConfirmedAtBays(long dealershipId, String bayType, TimeSlot window) {
        return findConfirmedAtBays(dealershipId, bayType, utc(window.start()), utc(window.end()));
    }

    @Query(nativeQuery = true, value = """
            SELECT * FROM appointment
             WHERE technician_id IN (:technicianIds)
               AND status = 'CONFIRMED'
               AND slot && tstzrange(:start, :end, '[)')""")
    List<AppointmentJpaEntity> findConfirmedForTechnicians(Collection<Long> technicianIds, OffsetDateTime start,
                                                           OffsetDateTime end);

    default List<AppointmentJpaEntity> findConfirmedForTechnicians(Collection<Long> technicianIds, TimeSlot window) {
        return findConfirmedForTechnicians(technicianIds, utc(window.start()), utc(window.end()));
    }

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE appointment SET status = 'CANCELLED', cancelled_at = :now
             WHERE id = :id AND status = 'CONFIRMED' AND starts_at > :now""")
    int markCancelled(UUID id, OffsetDateTime now);
}
