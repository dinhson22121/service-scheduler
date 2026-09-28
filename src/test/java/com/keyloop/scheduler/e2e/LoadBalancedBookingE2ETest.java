package com.keyloop.scheduler.e2e;

import static com.keyloop.scheduler.support.TestDates.london;
import static com.keyloop.scheduler.support.TestDates.workingDay;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.LongStream;

import com.keyloop.scheduler.support.Api;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class LoadBalancedBookingE2ETest {

    private static final String BASE_URL = System.getProperty("e2e.baseUrl", "http://localhost:8080");
    private static final List<String> MANAGEMENT_URLS = List.of("http://localhost:8081", "http://localhost:8082");
    private static final long LONDON = 1;
    private static final long WHEEL_ALIGNMENT = 3;
    private static final long FLEET_CUSTOMER = 4;
    private static final List<Long> FLEET = LongStream.rangeClosed(1001, 2000).boxed().toList();
    private static final Map<Long, Duration> SERVICE_DURATIONS = Map.of(
            1L, Duration.ofMinutes(60), 2L, Duration.ofMinutes(120), 3L, Duration.ofMinutes(90), 4L, Duration.ofMinutes(180));
    private static final Set<Integer> RETRYABLE = Set.of(429, 502, 503, 504);
    private static final int RATE_LIMIT_BURST = 100;
    private static final int MAX_ATTEMPTS = 40;
    private static final Duration PACING = Duration.ofMillis(12);
    private static final Duration KILL_AFTER = Duration.ofMillis(1500);

    private final Api api = new Api(BASE_URL);

    @BeforeAll
    static void stackIsUp() throws Exception {
        Api.Response response = new Api(BASE_URL).get("/v3/api-docs");
        assertThat(response.status()).as("docker compose stack must be running on " + BASE_URL).isEqualTo(200);
    }

    @Test
    void onlyOneOfManyConcurrentRequestsWinsTheSlotAcrossBothReplicas() throws Exception {
        LocalDate day = randomDay();
        String start = randomFreeStart(day, WHEEL_ALIGNMENT);
        List<Long> vehicles = vehiclesFreeOn(day, 50);
        List<Double> rejectedBefore = rejectedPerReplica();

        List<Api.Response> responses = releaseAtOnce(50, i ->
                send(booking(vehicles.get(i), WHEEL_ALIGNMENT, OffsetDateTime.parse(start))));

        assertThat(responses).filteredOn(r -> r.status() == 201).hasSize(1);
        assertThat(responses).filteredOn(r -> r.status() == 409).hasSize(49);
        List<Double> rejectedAfter = rejectedPerReplica();
        for (int replica = 0; replica < MANAGEMENT_URLS.size(); replica++) {
            assertThat(rejectedAfter.get(replica))
                    .as("replica %d took part in the race", replica + 1)
                    .isGreaterThan(rejectedBefore.get(replica));
        }
    }

    @Test
    void aClientAbove100RequestsPerSecondIsRateLimitedAtTheEdge() throws Exception {
        String unknown = "/api/v1/appointments/" + new UUID(0, 0);

        List<Api.Response> flood = releaseAtOnce(3 * RATE_LIMIT_BURST, i -> get(unknown));

        List<Api.Response> limited = flood.stream().filter(r -> r.status() == 429).toList();
        assertThat(flood.size() - limited.size()).as("the burst passes through").isGreaterThanOrEqualTo(RATE_LIMIT_BURST);
        assertThat(limited).as("the rest is rejected").isNotEmpty();
        assertThat(limited).allSatisfy(r -> {
            assertThat(r.header("Retry-After")).isEqualTo("1");
            assertThat(r.header("Content-Type")).contains("application/problem+json");
            assertThat(r.body().get("status").asInt()).isEqualTo(429);
        });

        Thread.sleep(1_500);
        assertThat(get(unknown).status()).as("the client recovers after Retry-After").isEqualTo(404);
    }

    @Test
    void killingAReplicaMidRunLosesNoBookingAndDuplicatesNone() throws Exception {
        warmUpBothReplicas();
        LocalDate day = randomDay();
        Set<String> confirmedBefore = ids(confirmedOn(day));
        Map<String, Map<String, Object>> requests = bookingsAcrossTheDay(day);
        Map<String, Api.Response> results = new ConcurrentHashMap<>();
        Map<String, Instant> answeredAt = new ConcurrentHashMap<>();
        AtomicInteger retries = new AtomicInteger();
        AtomicReference<Instant> killedAt = new AtomicReference<>();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            executor.submit(() -> {
                Thread.sleep(KILL_AFTER.toMillis());
                killedAt.set(Instant.now());
                docker("kill", "app1");
                return null;
            });
            int i = 0;
            for (var request : requests.entrySet()) {
                long delay = PACING.toMillis() * i++;
                executor.submit(() -> {
                    Thread.sleep(delay);
                    results.put(request.getKey(), sendWithRetry(request.getKey(), request.getValue(), retries));
                    answeredAt.put(request.getKey(), Instant.now());
                    return null;
                });
            }
        } finally {
            docker("start", "app1");
            awaitReady(MANAGEMENT_URLS.getFirst());
        }

        long answeredAfterKill = answeredAt.values().stream().filter(t -> t.isAfter(killedAt.get())).count();
        System.out.printf("requests=%d answered after the kill=%d retried after the kill=%d%n",
                requests.size(), answeredAfterKill, retries.get());
        assertThat(answeredAfterKill).as("the replica died mid-run").isBetween(1L, requests.size() - 1L);
        assertThat(results).hasSize(requests.size());
        assertThat(results.values()).allSatisfy(r -> assertThat(r.status()).isIn(201, 409));

        Map<String, String> bookedIdByKey = new LinkedHashMap<>();
        results.forEach((key, r) -> {
            if (r.status() == 201) {
                bookedIdByKey.put(key, r.body().get("id").asString());
            }
        });
        assertThat(new HashSet<>(bookedIdByKey.values())).as("one appointment per request").hasSize(bookedIdByKey.size());

        List<JsonNode> confirmedAfter = confirmedOn(day);
        Set<String> created = ids(confirmedAfter);
        created.removeAll(confirmedBefore);
        assertThat(created).as("every confirmed booking exists and nothing else was created")
                .containsExactlyInAnyOrderElementsOf(bookedIdByKey.values());
        assertNoOverlap(confirmedAfter, "serviceBayId");
        assertNoOverlap(confirmedAfter, "technicianId");
        assertNoOverlap(confirmedAfter, "vehicleId");
    }

    private Map<String, Map<String, Object>> bookingsAcrossTheDay(LocalDate day) throws Exception {
        List<Map.Entry<Long, LocalTime>> slots = new ArrayList<>();
        for (int copy = 0; copy < 5; copy++) {
            for (var service : SERVICE_DURATIONS.entrySet()) {
                for (LocalTime t = LocalTime.of(8, 0); !t.plus(service.getValue()).isAfter(LocalTime.of(18, 0));
                     t = t.plusMinutes(30)) {
                    slots.add(Map.entry(service.getKey(), t));
                }
            }
        }
        List<Long> vehicles = vehiclesFreeOn(day, slots.size());
        Map<String, Map<String, Object>> requests = new LinkedHashMap<>();
        for (int i = 0; i < slots.size(); i++) {
            var slot = slots.get(i);
            requests.put("request-" + i, booking(vehicles.get(i), slot.getKey(), london(day, slot.getValue().toString())));
        }
        return requests;
    }

    private List<Long> vehiclesFreeOn(LocalDate day, int needed) throws Exception {
        Set<Long> busy = new HashSet<>();
        confirmedOn(day).forEach(a -> busy.add(a.get("vehicleId").asLong()));
        List<Long> free = FLEET.stream().filter(v -> !busy.contains(v)).toList();
        assertThat(free).as("fleet vehicles free on " + day).hasSizeGreaterThanOrEqualTo(needed);
        return free.subList(0, needed);
    }

    private Api.Response sendWithRetry(String key, Map<String, Object> body, AtomicInteger retries)
            throws InterruptedException {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            Optional<Api.Response> response = postUnlessConnectionDropped(body)
                    .filter(r -> !RETRYABLE.contains(r.status()));
            if (response.isPresent()) {
                return response.get();
            }
            retries.incrementAndGet();
            Thread.sleep(250);
        }
        throw new AssertionError("no definitive answer for " + key + " after " + MAX_ATTEMPTS + " attempts");
    }

    private void warmUpBothReplicas() throws Exception {
        Map<String, Map<String, Object>> requests = bookingsAcrossTheDay(randomDay());
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            requests.forEach((key, body) -> executor.submit(() -> sendWithRetry(key, body, new AtomicInteger())));
        }
    }

    private Optional<Api.Response> postUnlessConnectionDropped(Map<String, Object> body)
            throws InterruptedException {
        try {
            return Optional.of(api.post("/api/v1/appointments", body));
        } catch (IOException replicaDiedMidRequest) {
            return Optional.empty();
        }
    }

    private Api.Response get(String path) {
        try {
            return api.get(path);
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private Api.Response send(Map<String, Object> body) {
        try {
            return api.post("/api/v1/appointments", body);
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<Api.Response> releaseAtOnce(int count, Function<Integer, Api.Response> call) throws Exception {
        CountDownLatch gate = new CountDownLatch(1);
        List<java.util.concurrent.Future<Api.Response>> futures = new ArrayList<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < count; i++) {
                int n = i;
                futures.add(executor.submit(() -> {
                    gate.await();
                    return call.apply(n);
                }));
            }
            gate.countDown();
            List<Api.Response> responses = new ArrayList<>();
            for (var future : futures) {
                responses.add(future.get());
            }
            return responses;
        }
    }

    private static Map<String, Object> booking(long vehicleId, long serviceTypeId, OffsetDateTime start) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("dealershipId", LONDON);
        body.put("customerId", FLEET_CUSTOMER);
        body.put("vehicleId", vehicleId);
        body.put("serviceTypeId", serviceTypeId);
        body.put("startTime", start.toString());
        return body;
    }

    private static LocalDate randomDay() {
        return workingDay(ThreadLocalRandom.current().nextInt(3, 60));
    }

    private String randomFreeStart(LocalDate day, long serviceTypeId) throws Exception {
        JsonNode slots = api.get("/api/v1/dealerships/%d/availability?serviceTypeId=%d&date=%s"
                .formatted(LONDON, serviceTypeId, day)).body().get("slots");
        assertThat(slots.size()).as("free slots on " + day).isPositive();
        return slots.get(ThreadLocalRandom.current().nextInt(slots.size())).get("startTime").asString();
    }

    private List<JsonNode> confirmedOn(LocalDate day) throws Exception {
        List<JsonNode> confirmed = new ArrayList<>();
        for (JsonNode appointment : api.get("/api/v1/dealerships/%d/appointments?date=%s".formatted(LONDON, day)).body()) {
            if ("CONFIRMED".equals(appointment.get("status").asString())) {
                confirmed.add(appointment);
            }
        }
        return confirmed;
    }

    private static Set<String> ids(Collection<JsonNode> appointments) {
        Set<String> ids = new HashSet<>();
        appointments.forEach(a -> ids.add(a.get("id").asString()));
        return ids;
    }

    private static void assertNoOverlap(List<JsonNode> appointments, String resourceField) {
        Map<Long, List<JsonNode>> byResource = new LinkedHashMap<>();
        appointments.forEach(a -> byResource.computeIfAbsent(a.get(resourceField).asLong(), k -> new ArrayList<>()).add(a));
        byResource.forEach((resource, booked) -> {
            booked.sort((a, b) -> a.get("startTime").asString().compareTo(b.get("startTime").asString()));
            for (int i = 1; i < booked.size(); i++) {
                Instant previousEnd = Instant.parse(booked.get(i - 1).get("endTime").asString());
                Instant start = Instant.parse(booked.get(i).get("startTime").asString());
                assertThat(start).as("%s %d double-booked", resourceField, resource).isAfterOrEqualTo(previousEnd);
            }
        });
    }

    private static List<Double> rejectedPerReplica() throws Exception {
        Pattern counter = Pattern.compile("scheduler_bookings_total\\{outcome=\"no_capacity\"[^}]*} ([0-9.E]+)");
        List<Double> values = new ArrayList<>();
        for (String url : MANAGEMENT_URLS) {
            Matcher m = counter.matcher(new Api(url).getText("/actuator/prometheus"));
            values.add(m.find() ? Double.parseDouble(m.group(1)) : 0.0);
        }
        return values;
    }

    private static void docker(String command, String service) throws IOException, InterruptedException {
        Process process = new ProcessBuilder("docker", "compose", command, service)
                .directory(new File(System.getProperty("user.dir")))
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes());
        assertThat(process.waitFor()).as("docker compose %s %s: %s", command, service, output).isZero();
    }

    private static void awaitReady(String managementUrl) throws InterruptedException {
        Api management = new Api(managementUrl);
        for (int i = 0; i < 120; i++) {
            if (isReady(management)) {
                return;
            }
            Thread.sleep(500);
        }
        throw new AssertionError(managementUrl + " did not become ready");
    }

    private static boolean isReady(Api management) throws InterruptedException {
        try {
            return management.get("/actuator/health/readiness").status() == 200;
        } catch (IOException stillStarting) {
            return false;
        }
    }
}
