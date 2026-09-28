package com.keyloop.scheduler.support;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.server.port=0")
@Import(IntegrationTest.Postgres.class)
public abstract class IntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class Postgres {

        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer("postgres:17-alpine");
        }
    }

    public static final long LONDON = 1;
    public static final long SAIGON = 2;
    public static final long OIL_CHANGE = 1;
    public static final long BRAKE_SERVICE = 2;
    public static final long WHEEL_ALIGNMENT = 3;
    public static final long ANNUAL_SERVICE = 4;
    public static final long FLEET_CUSTOMER = 4;
    private static final long FIRST_FLEET_VEHICLE = 1001;

    @Autowired
    protected JdbcClient jdbc;

    @Value("${local.server.port}")
    private int port;

    protected Api api;

    @BeforeEach
    void resetAppointments() {
        api = new Api("http://localhost:" + port);
        jdbc.sql("TRUNCATE appointment").update();
    }

    protected static Map<String, Object> booking(long dealershipId, long serviceTypeId, OffsetDateTime start) {
        long customerId = dealershipId == SAIGON ? 3 : 1;
        long vehicleId = dealershipId == SAIGON ? 4 : 1;
        return booking(dealershipId, customerId, vehicleId, serviceTypeId, start);
    }

    protected static Map<String, Object> booking(long dealershipId, long customerId, long vehicleId,
                                                 long serviceTypeId, OffsetDateTime start) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("dealershipId", dealershipId);
        body.put("customerId", customerId);
        body.put("vehicleId", vehicleId);
        body.put("serviceTypeId", serviceTypeId);
        body.put("startTime", start.toString());
        return body;
    }

    protected static Map<String, Object> fleetBooking(int vehicle, long dealershipId, long serviceTypeId,
                                                      OffsetDateTime start) {
        return booking(dealershipId, FLEET_CUSTOMER, FIRST_FLEET_VEHICLE + vehicle, serviceTypeId, start);
    }

    protected Api.Response book(Map<String, Object> body) throws Exception {
        return api.post("/api/v1/appointments", body);
    }

    protected int confirmedCount() {
        return jdbc.sql("SELECT count(*) FROM appointment WHERE status = 'CONFIRMED'").query(Integer.class).single();
    }
}
