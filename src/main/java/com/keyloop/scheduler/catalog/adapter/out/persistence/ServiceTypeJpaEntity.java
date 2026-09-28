package com.keyloop.scheduler.catalog.adapter.out.persistence;

import java.time.Duration;

import com.keyloop.scheduler.catalog.domain.ServiceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "service_type")
class ServiceTypeJpaEntity {

    @Id
    private Long id;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int durationMinutes;

    @Column(nullable = false)
    private String requiredSkill;

    @Column(nullable = false)
    private String requiredBayType;

    protected ServiceTypeJpaEntity() {
    }

    ServiceType toDomain() {
        return new ServiceType(id, code, name, Duration.ofMinutes(durationMinutes), requiredSkill, requiredBayType);
    }
}
