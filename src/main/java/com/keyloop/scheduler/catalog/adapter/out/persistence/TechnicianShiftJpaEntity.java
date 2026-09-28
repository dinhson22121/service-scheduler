package com.keyloop.scheduler.catalog.adapter.out.persistence;

import java.time.Instant;

import com.keyloop.scheduler.shared.domain.TimeSlot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "technician_shift")
public class TechnicianShiftJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long technicianId;

    @Column(nullable = false)
    private Instant startsAt;

    @Column(nullable = false)
    private Instant endsAt;

    protected TechnicianShiftJpaEntity() {
    }

    public long technicianId() {
        return technicianId;
    }

    public TimeSlot slot() {
        return new TimeSlot(startsAt, endsAt);
    }
}
