package com.keyloop.scheduler;

import static com.keyloop.scheduler.support.TestDates.london;
import static com.keyloop.scheduler.support.TestDates.workingDay;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.UUID;

import javax.sql.DataSource;

import com.keyloop.scheduler.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class SchemaIntegrationTest extends IntegrationTest {

    private static final OffsetDateTime NINE = london(workingDay(9), "09:00");

    @Autowired
    private DataSource dataSource;

    @Test
    void theDatabaseItselfRejectsAnOverlappingBookingOfTheSameBay() {
        insertConfirmed(1, 1, 1, NINE);

        assertThatThrownBy(() -> insertConfirmed(2, 1, 2, NINE.plusMinutes(30)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("appointment_no_bay_overlap");
    }

    @Test
    void theDatabaseItselfRejectsAnOverlappingBookingOfTheSameTechnician() {
        insertConfirmed(1, 1, 1, NINE);

        assertThatThrownBy(() -> insertConfirmed(2, 2, 1, NINE.plusMinutes(30)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("appointment_no_technician_overlap");
    }

    @Test
    void theDatabaseItselfRejectsAnOverlappingBookingOfTheSameVehicle() {
        insertConfirmed(1, 1, 1, NINE);

        assertThatThrownBy(() -> insertConfirmed(1, 2, 2, NINE.plusMinutes(30)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("appointment_no_vehicle_overlap");
    }

    @Test
    void backToBackBookingsOfTheSameResourcesAreAccepted() {
        insertConfirmed(1, 1, 1, NINE);
        insertConfirmed(1, 1, 1, NINE.plusHours(1));

        assertThat(confirmedCount()).isEqualTo(2);
    }

    @Test
    void theSchemaAndSeedScriptsCanRunAgainOnAPopulatedDatabase() {
        insertConfirmed(1, 1, 1, NINE);
        int constraints = constraintCount();
        int shifts = shiftCount();

        runScripts("db/constraints.sql", "db/seed/demo.sql");

        assertThat(constraintCount()).isEqualTo(constraints).isGreaterThanOrEqualTo(24);
        assertThat(shiftCount()).isEqualTo(shifts);
        assertThat(confirmedCount()).isEqualTo(1);
    }

    private void insertConfirmed(long vehicleId, long bayId, long technicianId, OffsetDateTime start) {
        jdbc.sql("""
                        INSERT INTO appointment (id, dealership_id, customer_id, vehicle_id, service_type_id,
                            service_bay_id, technician_id, starts_at, ends_at, status, created_at)
                        VALUES (:id, 1, 1, :vehicle, 1, :bay, :technician, :start, :end, 'CONFIRMED', now())""")
                .param("id", UUID.randomUUID())
                .param("vehicle", vehicleId)
                .param("bay", bayId)
                .param("technician", technicianId)
                .param("start", start)
                .param("end", start.plusHours(1))
                .update();
    }

    private int constraintCount() {
        return jdbc.sql("""
                        SELECT count(*) FROM pg_constraint c JOIN pg_class t ON t.oid = c.conrelid
                         WHERE t.relname IN ('dealership', 'service_type', 'service_bay', 'technician',
                               'technician_skill', 'technician_shift', 'customer', 'vehicle', 'appointment')
                           AND c.contype IN ('c', 'f', 'u', 'x')""")
                .query(Integer.class).single();
    }

    private int shiftCount() {
        return jdbc.sql("SELECT count(*) FROM technician_shift").query(Integer.class).single();
    }

    private void runScripts(String... locations) {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.setSeparator(ScriptUtils.EOF_STATEMENT_SEPARATOR);
        for (String location : locations) {
            populator.addScript(new ClassPathResource(location));
        }
        populator.execute(dataSource);
    }
}
