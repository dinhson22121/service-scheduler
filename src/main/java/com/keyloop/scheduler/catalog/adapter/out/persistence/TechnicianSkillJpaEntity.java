package com.keyloop.scheduler.catalog.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "technician_skill")
class TechnicianSkillJpaEntity {

    @Embeddable
    public record Key(@Column(nullable = false) Long technicianId, @Column(nullable = false) String skill) {
    }

    @EmbeddedId
    private Key key;

    protected TechnicianSkillJpaEntity() {
    }
}
