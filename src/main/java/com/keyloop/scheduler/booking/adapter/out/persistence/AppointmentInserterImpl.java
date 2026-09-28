package com.keyloop.scheduler.booking.adapter.out.persistence;

import jakarta.persistence.EntityManager;
import org.springframework.transaction.support.TransactionTemplate;

class AppointmentInserterImpl implements AppointmentInserter {

    private static final String LOCK_TIMEOUT = "500ms";

    private final EntityManager entityManager;
    private final TransactionTemplate transaction;

    AppointmentInserterImpl(EntityManager entityManager, TransactionTemplate transaction) {
        this.entityManager = entityManager;
        this.transaction = transaction;
    }

    @Override
    public void insert(AppointmentJpaEntity appointment) {
        transaction.executeWithoutResult(status -> {
            entityManager.createNativeQuery("SET LOCAL lock_timeout = '" + LOCK_TIMEOUT + "'").executeUpdate();
            lockResource("vehicle", appointment.vehicleId());
            lockResource("bay", appointment.serviceBayId());
            lockResource("technician", appointment.technicianId());
            entityManager.persist(appointment);
            entityManager.flush();
        });
    }

    private void lockResource(String kind, long id) {
        entityManager.createNativeQuery("SELECT CAST(pg_advisory_xact_lock(hashtextextended(:resource, 0)) AS text)")
                .setParameter("resource", kind + ":" + id)
                .getSingleResult();
    }
}
