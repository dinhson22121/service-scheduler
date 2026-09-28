package com.keyloop.scheduler.catalog.adapter.out.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface VehicleJpaRepository extends JpaRepository<VehicleJpaEntity, Long> {

    @Query("select v.customerId from VehicleJpaEntity v where v.id = :vehicleId")
    Optional<Long> findOwnerId(long vehicleId);
}
