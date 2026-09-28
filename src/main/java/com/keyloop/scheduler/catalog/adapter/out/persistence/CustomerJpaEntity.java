package com.keyloop.scheduler.catalog.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "customer")
class CustomerJpaEntity {

    @Id
    private Long id;

    @Column(nullable = false)
    private String name;

    private String email;

    protected CustomerJpaEntity() {
    }
}
