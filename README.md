# Unified Service Scheduler

The backend for Keyloop's Scenario A. It books a service appointment for a vehicle at a dealership and assigns a **service bay** and a **qualified technician** who are both free for the **whole service duration**. It never double-books, even with many replicas taking requests at the same moment.

- Design document: [`docs/design.md`](docs/design.md), also as [`docs/design.pdf`](docs/design.pdf) (architecture, data flow, concurrency model, observability, assumptions)
- API contract: [`docs/openapi.yaml`](docs/openapi.yaml); Swagger UI at `http://localhost:8080/swagger-ui.html` when running
- AI collaboration log: [`docs/ai-log.md`](docs/ai-log.md)

## Run

Prerequisites: Docker. JDK 21 only if you want to run the tests (the Maven wrapper downloads Maven itself).

```bash
docker compose up -d --build --wait     # postgres + 2 app replicas + nginx on :8080
docker compose down -v                  # stop and wipe the database
```

The stack starts from an empty database. At startup Hibernate creates the tables (`ddl-auto: update`), `db/constraints.sql` adds what JPA cannot express (exclusion constraints, range columns, composite foreign keys, checks), and `db/seed/demo.sql` loads demo reference data: two dealerships (London and Saigon, in different time zones), bays, technicians with skills and shifts for the next 90 days (Monday to Saturday), customers and vehicles.

## Try it

```bash
DAY=$(date -d 'next tuesday' +%F 2>/dev/null || date -v+1d -v+tue +%F)   # next Tuesday (GNU || BSD/macOS date)

# Free start times for a brake service (service type 2) at the Saigon dealership (UTC+7, no daylight saving)
curl "localhost:8080/api/v1/dealerships/2/availability?serviceTypeId=2&date=$DAY"

# Book it. A vehicle holds one appointment at a time, so sending the same request again is safe.
curl -i -X POST localhost:8080/api/v1/appointments -H 'Content-Type: application/json' \
  -d "{\"dealershipId\":2,\"customerId\":3,\"vehicleId\":4,\"serviceTypeId\":2,\"startTime\":\"${DAY}T09:00:00+07:00\"}"
# -> 201, Location: /api/v1/appointments/{id}, technician 4 (the only brakes technician in Saigon), bay 4 or 5

# Same request again -> 201 with the same appointment and "Idempotent-Replayed: true"
# Same vehicle, a different overlapping booking -> 409 "Vehicle already booked"
# Another customer, overlapping slot -> 409 application/problem+json "No technician qualified for BRAKES…"
curl -X POST localhost:8080/api/v1/appointments -H 'Content-Type: application/json' \
  -d "{\"dealershipId\":2,\"customerId\":1,\"vehicleId\":1,\"serviceTypeId\":2,\"startTime\":\"${DAY}T10:00:00+07:00\"}"

curl "localhost:8080/api/v1/dealerships/2/appointments?date=$DAY"     # the day's appointments
curl -X POST localhost:8080/api/v1/appointments/{id}/cancel           # frees the bay, technician and vehicle
```

### Demo reference data

A service type decides the slot length, the skill a technician needs, and the bay type the job needs:

| `serviceTypeId` | Service | Duration | Required skill | Required bay type |
|---|---|---|---|---|
| `1` | Oil and filter change | 60 min | `GENERAL_SERVICE` | `GENERAL` |
| `2` | Brake pads and discs | 120 min | `BRAKES` | `GENERAL` |
| `3` | Wheel alignment | 90 min | `ALIGNMENT` | `ALIGNMENT` (alignment rig) |
| `4` | Annual service | 180 min | `GENERAL_SERVICE` | `GENERAL` |

| `dealershipId` | Dealership | Zone, opening hours | Bays (id: type) | Technicians (id: skills, shift) |
|---|---|---|---|---|
| `1` | London | `Europe/London`, 08:00–18:00 | `1`, `2`: `GENERAL`; `3`: `ALIGNMENT` | `1` Alice: general, brakes, 08–16 · `2` Ben: general, alignment, 10–18 · `3` Chloe: brakes, alignment, 08–16 |
| `2` | Saigon | `Asia/Ho_Chi_Minh`, 07:30–17:30 | `4`, `5`: `GENERAL` | `4` An: general, brakes, 07:30–17:30 · `5` Binh: general, 07:30–17:30 |

Shifts are seeded Monday to Saturday for the next 90 days, so Sundays have no availability. Saigon has no alignment bay, so service type `3` there always gets `409`.

Customer `1` owns vehicles `1` and `2`, customer `2` owns vehicle `3`, customer `3` owns vehicle `4`, and customer `4` is a leasing fleet with vehicles `1001`–`2000`, used by the concurrency, E2E and demo races. Customers are not tied to a dealership.

## Test

```bash
./mvnw verify                 # unit + integration tests (Testcontainers Postgres), coverage gate at 80% lines
docker compose up -d --build --wait
./mvnw -Pe2e test             # end-to-end through nginx against both replicas, including killing one
./mvnw test -Dtest=OpenApiContractTest -Dopenapi.update=true   # regenerate docs/openapi.yaml after an API change
```

Testcontainers finds Docker Desktop on its own. With colima (or another non-default socket), point it at the socket first:

```bash
export DOCKER_HOST="unix://$HOME/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

| Suite | What it proves |
|---|---|
| Unit | Time slots, opening hours across DST, the availability calculator, and every branch of the booking retry loop |
| Architecture | ArchUnit rules: plain-Java domain, application depends on ports only, contexts meet through domain and ports |
| Integration | All endpoints and error codes on a real Postgres, shifts and skills, time zones, repeated requests, one appointment per vehicle at a time, cancellation, 503 when the pool is exhausted, metrics and health endpoints |
| Concurrency | 50 simultaneous requests for the only alignment bay give exactly **1** booking and 49 `409`. For capacity 2, exactly 2 win. 20 identical requests give 1 booking, and 20 different bookings of one vehicle give 1. |
| E2E | The same race through the load balancer, with both replicas taking part (checked through their metrics). Then 345 bookings while `app1` is **SIGKILLed** mid-run: every request gets a definitive answer, and nothing is lost, duplicated or double-booked. A client flooding past 100 req/s gets `429` with `Retry-After` and recovers a second later. |

CI (`.github/workflows/ci.yml`) runs both suites.

Load tests (k6, in Docker): [`loadtest/README.md`](loadtest/README.md). Results: [`docs/loadtest-report.md`](docs/loadtest-report.md).

## Operate

| | |
|---|---|
| Rate limit | 100 req/s per client IP, burst 100, on `/api/` at nginx (`docker/nginx.conf`); `429` + `Retry-After: 1` as `application/problem+json`. The load-test stack uses its own nginx configs without it, so capacity numbers are unaffected. |
| Health | `:8081/actuator/health/{liveness,readiness}` for replica 1, `:8082` for replica 2. Not exposed through nginx. |
| Metrics | `:8081/actuator/prometheus`: `scheduler_bookings_total{outcome}`, `scheduler_booking_conflicts_total{resource}`, `scheduler_cancellations_total`, HTTP latency histograms, Hikari pool metrics |
| Logs | JSON (ECS) in containers. Each line carries `traceId`, which responses also return as `X-Trace-Id`. |
| Config | Environment variables: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `DB_POOL_SIZE`, `DB_CONNECTION_TIMEOUT_MS`, `DB_STATEMENT_TIMEOUT_MS`, `TOMCAT_MAX_THREADS`, `SEED_LOCATIONS` (defaults to the demo seed; point it at another script, e.g. `optional:classpath:db/seed/none.sql`, to skip demo data) |

## Key decisions and trade-offs

- **The database is the arbiter.** Three Postgres exclusion constraints (`bay WITH =, slot WITH &&`, and the same for the technician and the vehicle, on confirmed rows) make an overlapping booking impossible, whichever replica inserts it. The app is stateless, and nothing in memory decides an outcome.
- **Per-resource advisory locks, taken in a fixed order, before the insert.** They were added because a test showed that concurrent inserters deadlock inside the exclusion check (resolved only after 1 s). They order the writers (vehicle, then bay, then technician); they do not replace the constraints. Details: [design §5](docs/design.md#5-concurrency-and-correctness-the-core).
- **Retries are safe with no client-side key.** A vehicle holds one appointment at a time, so the vehicle and slot identify a booking: the same request sent again returns it, and a different overlapping one gets `409`. nginx does not replay a `POST`; the client sends the same request again.
- **Availability is advisory**, and booking checks again atomically. That keeps the read path cheap and ready for a read replica.
- **Bounded waits.** Each database call waits at most 2 s for a connection, 5 s for a statement and 500 ms for a lock; a timeout becomes `503` with `Retry-After`. A request has no overall deadline yet, so past capacity it can still queue until nginx answers `504`. Either way the client sends the same request again ([load test report](docs/loadtest-report.md)).
- **Rate limit at the edge.** nginx allows 100 requests per second per client IP (burst 100) on `/api/`, and answers `429` with `Retry-After: 1` beyond that. It is the single entry point, so its counters are shared by every replica; with several load-balancer nodes this moves to the API gateway, keyed by the authenticated client (design §10).
- **Lean on purpose.** No Kafka, Redis, custom error codes or auth. They would not carry the core problem. Authentication belongs at the gateway (see design §10 and §11).

## AI Collaboration Narrative

**Strategy.** I used Claude Code as an engineer I direct, not an autocomplete. Before any design, I gave it written ground rules: plan first and wait for my confirmation, design for 2+ stateless nodes with correctness enforced in the database, make every write retry-safe, prove it by killing a replica, and keep everything else lean. I made the product decisions (scenario, layer, scope, what stays out) and let the AI propose, implement and test within those limits.

**Verification.** I accepted nothing on the AI's word. Each step had to end in evidence: a test that could fail, the logs of both replicas, metrics split across replicas. This caught real problems:

- Timestamps differed between POST and GET, because of nanosecond versus microsecond precision.
- A concurrent retry of the same booking was told `409` for a booking it actually owned.
- Most important, concurrent inserts **deadlocked** inside the exclusion constraint. That turned the "obvious" design into one with per-resource lock ordering.

I also rejected a test that passed but proved nothing: the first kill-replica test killed the node after the traffic had finished. It was rewritten to assert that the kill happened mid-run.

The challenge ran both ways. I wanted the backend, not the client, to own retry safety, and proposed the license plate as the booking key. The AI pushed back (a plate alone allows one booking per vehicle, ever, and plates change) and proposed the vehicle plus the slot, which also closed a gap: a vehicle could be booked into two bays at once. It warned that the new constraint would reopen the deadlock unless the vehicle joined the lock order. The committed test passed with or without that lock, so I asked for a probe that could tell them apart: two lock timeouts in 1,500 requests without it, none with it.

**Ownership of quality.** I kept the decisions the AI tried to make for me (it had assumed the backend layer before I chose it), and questioned suggestions against the scaling rules: I first rejected nginx rate limiting as per-instance state, then added it once I saw that in this stack nginx is the single entry point, so its counters are shared (with more load-balancer nodes it belongs at the gateway). An independent reviewer agent audited the code, and each finding was checked before being fixed. The whole trail, including the mistakes, is in [`docs/ai-log.md`](docs/ai-log.md).

## Layout

```
src/main/java/com/keyloop/scheduler/     hexagonal, one package per bounded context (design §2)
  booking/        book, read and cancel appointments: retry, replay of repeated requests, advisory-lock insert
  availability/   free start times: pure calculator + read model
  catalog/        read-only reference data, opening hours in the dealership's zone
  shared/         TimeSlot, domain exceptions, ProblemDetail and 503 mapping, trace-id header, OpenAPI info
    each context: domain/                      plain Java records and rules
                  application/ (+ port/in, port/out)  use cases and the ports they need
                  adapter/in/web/              controllers and DTOs
                  adapter/out/persistence/     JPA entities + Spring Data repositories (all DB access)
src/main/resources/db/constraints.sql   constraints, range columns and indexes JPA cannot create (idempotent, runs at startup)
src/main/resources/db/seed/demo.sql     demo reference data (idempotent, optional)
src/test/java/…                   unit, integration, concurrency, architecture, e2e (profile e2e)
docker-compose.yml, docker/nginx.conf, Dockerfile
```
