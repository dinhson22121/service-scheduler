package com.keyloop.scheduler.catalog.adapter.out.persistence;

import java.time.LocalTime;
import java.time.ZoneId;

import com.keyloop.scheduler.catalog.domain.Dealership;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "dealership")
class DealershipJpaEntity {

    @Id
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String timezone;

    @Column(nullable = false)
    private LocalTime opensAt;

    @Column(nullable = false)
    private LocalTime closesAt;

    protected DealershipJpaEntity() {
    }

    Dealership toDomain() {
        return new Dealership(id, name, ZoneId.of(timezone), opensAt, closesAt);
    }
}
