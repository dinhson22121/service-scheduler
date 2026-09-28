package com.keyloop.scheduler.catalog.application.port.out;

import java.util.Optional;

import com.keyloop.scheduler.catalog.domain.Dealership;
import com.keyloop.scheduler.catalog.domain.ServiceType;

public interface CatalogRepository {

    Optional<Dealership> findDealership(long id);

    Optional<ServiceType> findServiceType(long id);

    Optional<Long> findVehicleOwner(long vehicleId);

    boolean customerExists(long customerId);
}
