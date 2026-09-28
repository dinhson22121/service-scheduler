package com.keyloop.scheduler.catalog.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "technician")
class TechnicianJpaEntity {

    @Id
    private Long id;

    @Column(nullable = false)
    private Long dealershipId;

    @Column(nullable = false)
    private String name;

    protected TechnicianJpaEntity() {
    }
}
