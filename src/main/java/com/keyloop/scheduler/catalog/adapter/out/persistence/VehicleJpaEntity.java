package com.keyloop.scheduler.catalog.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "vehicle")
class VehicleJpaEntity {

    @Id
    private Long id;

    @Column(nullable = false)
    private Long customerId;

    @Column(nullable = false)
    private String vin;

    @Column(nullable = false)
    private String make;

    @Column(nullable = false)
    private String model;

    protected VehicleJpaEntity() {
    }
}
