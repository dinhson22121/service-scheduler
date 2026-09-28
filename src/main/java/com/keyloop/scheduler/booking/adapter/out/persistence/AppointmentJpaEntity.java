package com.keyloop.scheduler.booking.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;

import com.keyloop.scheduler.booking.domain.Appointment;
import com.keyloop.scheduler.booking.domain.AppointmentStatus;
import com.keyloop.scheduler.shared.domain.TimeSlot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "appointment")
public class AppointmentJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private Long dealershipId;

    @Column(nullable = false)
    private Long customerId;

    @Column(nullable = false)
    private Long vehicleId;

    @Column(nullable = false)
    private Long serviceTypeId;

    @Column(nullable = false)
    private Long serviceBayId;

    @Column(nullable = false)
    private Long technicianId;

    @Column(nullable = false)
    private Instant startsAt;

    @Column(nullable = false)
    private Instant endsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppointmentStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant cancelledAt;

    protected AppointmentJpaEntity() {
    }

    static AppointmentJpaEntity from(Appointment a) {
        AppointmentJpaEntity entity = new AppointmentJpaEntity();
        entity.id = a.id();
        entity.dealershipId = a.dealershipId();
        entity.customerId = a.customerId();
        entity.vehicleId = a.vehicleId();
        entity.serviceTypeId = a.serviceTypeId();
        entity.serviceBayId = a.serviceBayId();
        entity.technicianId = a.technicianId();
        entity.startsAt = a.slot().start();
        entity.endsAt = a.slot().end();
        entity.status = a.status();
        entity.createdAt = a.createdAt();
        entity.cancelledAt = a.cancelledAt();
        return entity;
    }

    Appointment toDomain() {
        return new Appointment(id, dealershipId, customerId, vehicleId, serviceTypeId, serviceBayId, technicianId,
                slot(), status, createdAt, cancelledAt);
    }

    public long vehicleId() {
        return vehicleId;
    }

    public long serviceBayId() {
        return serviceBayId;
    }

    public long technicianId() {
        return technicianId;
    }

    public TimeSlot slot() {
        return new TimeSlot(startsAt, endsAt);
    }
}
