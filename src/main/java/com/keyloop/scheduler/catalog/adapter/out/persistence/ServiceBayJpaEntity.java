package com.keyloop.scheduler.catalog.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "service_bay")
public class ServiceBayJpaEntity {

    @Id
    private Long id;

    @Column(nullable = false)
    private Long dealershipId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String bayType;

    protected ServiceBayJpaEntity() {
    }
}
