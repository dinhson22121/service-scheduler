package com.keyloop.scheduler.booking.adapter.out.persistence;

import static com.keyloop.scheduler.shared.adapter.out.persistence.UtcTimestamps.utc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.keyloop.scheduler.shared.domain.TimeSlot;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

interface CandidateJpaRepository extends Repository<AppointmentJpaEntity, UUID> {

    @Query(nativeQuery = true, value = """
            SELECT b.id
              FROM service_bay b
             WHERE b.dealership_id = :dealershipId
               AND b.bay_type = :bayType
               AND NOT EXISTS (
                   SELECT 1 FROM appointment a
                    WHERE a.service_bay_id = b.id
                      AND a.status = 'CONFIRMED'
                      AND a.slot && tstzrange(:start, :end, '[)'))""")
    List<Long> freeBays(long dealershipId, String bayType, OffsetDateTime start, OffsetDateTime end);

    default List<Long> freeBays(long dealershipId, String bayType, TimeSlot slot) {
        return freeBays(dealershipId, bayType, utc(slot.start()), utc(slot.end()));
    }

    @Query(nativeQuery = true, value = """
            SELECT t.id
              FROM technician t
              JOIN technician_skill s ON s.technician_id = t.id AND s.skill = :skill
             CROSS JOIN LATERAL (
                   SELECT 1 FROM technician_shift sh
                    WHERE sh.technician_id = t.id
                      AND sh.shift @> tstzrange(:start, :end, '[)')
                    LIMIT 1) on_shift
             WHERE t.dealership_id = :dealershipId
               AND NOT EXISTS (
                   SELECT 1 FROM appointment a
                    WHERE a.technician_id = t.id
                      AND a.status = 'CONFIRMED'
                      AND a.slot && tstzrange(:start, :end, '[)'))""")
    List<Long> freeTechnicians(long dealershipId, String skill, OffsetDateTime start, OffsetDateTime end);

    default List<Long> freeTechnicians(long dealershipId, String skill, TimeSlot slot) {
        return freeTechnicians(dealershipId, skill, utc(slot.start()), utc(slot.end()));
    }
}
