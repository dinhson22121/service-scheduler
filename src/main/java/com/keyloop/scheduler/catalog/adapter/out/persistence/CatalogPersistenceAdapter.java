package com.keyloop.scheduler.catalog.adapter.out.persistence;

import java.util.Optional;

import com.keyloop.scheduler.catalog.application.port.out.CatalogRepository;
import com.keyloop.scheduler.catalog.domain.Dealership;
import com.keyloop.scheduler.catalog.domain.ServiceType;
import org.springframework.stereotype.Component;

@Component
class CatalogPersistenceAdapter implements CatalogRepository {

    private final DealershipJpaRepository dealerships;
    private final ServiceTypeJpaRepository serviceTypes;
    private final CustomerJpaRepository customers;
    private final VehicleJpaRepository vehicles;

    CatalogPersistenceAdapter(DealershipJpaRepository dealerships, ServiceTypeJpaRepository serviceTypes,
                              CustomerJpaRepository customers, VehicleJpaRepository vehicles) {
        this.dealerships = dealerships;
        this.serviceTypes = serviceTypes;
        this.customers = customers;
        this.vehicles = vehicles;
    }

    @Override
    public Optional<Dealership> findDealership(long id) {
        return dealerships.findById(id).map(DealershipJpaEntity::toDomain);
    }

    @Override
    public Optional<ServiceType> findServiceType(long id) {
        return serviceTypes.findById(id).map(ServiceTypeJpaEntity::toDomain);
    }

    @Override
    public Optional<Long> findVehicleOwner(long vehicleId) {
        return vehicles.findOwnerId(vehicleId);
    }

    @Override
    public boolean customerExists(long customerId) {
        return customers.existsById(customerId);
    }
}
