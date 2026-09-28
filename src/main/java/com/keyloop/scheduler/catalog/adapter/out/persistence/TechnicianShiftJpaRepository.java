package com.keyloop.scheduler.catalog.adapter.out.persistence;

import static com.keyloop.scheduler.shared.adapter.out.persistence.UtcTimestamps.utc;

import java.time.OffsetDateTime;
import java.util.List;

import com.keyloop.scheduler.shared.domain.TimeSlot;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface TechnicianShiftJpaRepository extends Repository<TechnicianShiftJpaEntity, Long> {

    @Query(nativeQuery = true, value = """
            SELECT sh.*
              FROM technician t
              JOIN technician_skill s ON s.technician_id = t.id AND s.skill = :skill
              JOIN technician_shift sh ON sh.technician_id = t.id
             WHERE t.dealership_id = :dealershipId
               AND sh.shift && tstzrange(:start, :end, '[)')""")
    List<TechnicianShiftJpaEntity> findQualified(long dealershipId, String skill, OffsetDateTime start, OffsetDateTime end);

    default List<TechnicianShiftJpaEntity> findQualified(long dealershipId, String skill, TimeSlot window) {
        return findQualified(dealershipId, skill, utc(window.start()), utc(window.end()));
    }
}
