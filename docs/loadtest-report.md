# Load test report

Two rounds. Harness and how to reproduce: [`loadtest/README.md`](../loadtest/README.md).

- **Round 2 (2026-09-24)** measures the current code: after the move from Flyway to JPA `ddl-auto`, with and
  without the rewrite of the booking candidate query. Its numbers replace round 1's capacity figures.
- **Round 1 (2026-09-23, Flyway schema, before the `ddl-auto` switch)** found the problems that round 2 followed up. Its
  findings on overload behaviour, the hot slot and the soak still describe the design (timeouts, locking and
  pool settings have not changed), but its capacity numbers and bottleneck no longer apply.
- Both rounds predate the switch from a client `Idempotency-Key` to the vehicle-and-slot identity
  (2026-09-29). That switch adds one indexed lookup and one advisory lock per booking; it has not been
  re-measured under load.

## Round 2: current code

### Summary

- **No double booking.** 0 overlapping bay or technician bookings in every run.
- **Both builds meet the targets up to 200 req/s, the highest level tested** (round 1: knee at 100–150 req/s).
  The knee was not reached. At 200 req/s k6 (capped at 1 CPU) starts dropping iterations, so higher levels
  need a bigger load generator before they mean anything.
- **Round 1's main bottleneck is gone on the current schema.** The availability shift lookup, which round 1
  measured at about 95 % of database time, is now planned as a per-technician index lookup (about 75 buffers
  instead of about 380). The cause of the plan change was not isolated; the likely one is different indexes
  and statistics between the Flyway schema and the one Hibernate plus `db/constraints.sql` creates (the
  generic plan now uses `technician_skill_skill_idx`). The plan is chosen from statistics, so it could change
  again; `pg_stat_statements` is the place to watch.
- **The booking candidate query still had the bad plan, and was rewritten.** Its `EXISTS` on shifts became a
  hash semi-join over the shifts of all 502 technicians. With `CROSS JOIN LATERAL (… LIMIT 1)` it is a
  per-technician lookup: **411 → 86 buffers, 5.6 → 1.6 ms** (generic plan). Under load it lowered Postgres
  CPU by about a quarter and cut p99 at 150 req/s by 2.5–5×; the 1.7× gain at 200 req/s is within run-to-run
  noise (table below).
- **nginx `max_fails` raised from 1 to 3** (round 1, recommendation 4). Not re-verified under load: the
  overload runs below slowed requests down but produced no timeouts, so nginx never marked a replica as failed
  in either configuration.

### Ramp: before and after the booking-query rewrite

Same host, caps, data and pass criteria as round 1 ([Setup](#setup)). 2 replicas, pool 10, 60 s per level, run back to back on a quiet host (1-minute load average about 7–8 on 8
hardware threads during both runs). Search p95 / p99 and booking p95 / p99 in ms.

| Target req/s | Before (current `main`) | After (query rewrite) |
|---|---|---|
| 25 | fail: 26 / 83, **50 / 538** (cold JVM) | fail: **108 / 731, 126 / 725** (cold JVM) |
| 50 | pass: 11 / 15, 27 / 35 | pass: 37 / 89, 98 / 144 |
| 75 | pass: 9 / 10, 21 / 23 | pass: 12 / 24, 28 / 42 |
| 100 | pass: 97 / 490, 133 / 478 | pass: 17 / 63, 33 / 90 |
| 150 | pass: 80 / 199, 121 / 320 | pass: **16 / 81, 26 / 61** |
| 200 | pass: 53 / 188, 91 / 203; 22 waiting for a connection | pass: **36 / 110, 52 / 117**; 0 waiting |

| | Before | After |
|---|---|---|
| Postgres CPU, average / highest sample (cap 200) | 51 / 228 | **39 / 139** |
| Each replica, average CPU (cap 200) | 50 / 49 | 48 / 49 |
| Confirmed bookings (= k6 `201`s) | 3,462 | 3,497 |
| k6 dropped iterations | 71 | 97 |

The first phase fails in both builds: a replica that has just started is slow until the JIT has warmed up
(design §5, cold start). Single samples of `docker stats` can exceed a container's CPU cap; the averages are
the useful figure.

### Overload with a 2-connection pool

To see nginx's behaviour past capacity, both replicas ran with `DB_POOL_SIZE=2`, at 100 then 200 req/s, once
with `max_fails=1` and once with `max_fails=3`.

| | `max_fails=1` | `max_fails=3` |
|---|---|---|
| 100 req/s: search p99, booking p99 | 1,293 ms, 1,654 ms (fail) | 2,604 ms, 3,094 ms (fail) |
| 200 req/s: search p99, booking p99 | 209 ms, 226 ms (pass) | 849 ms, 900 ms (fail) |
| Requests waiting for a connection, max | 47 | 48 |
| `502` / `503` / `504`, nginx `upstream timed out` | 0 / 0 / 0, 0 | 0 / 0 / 0, 0 |

Requests queued for seconds but none reached a timeout, so this did not exercise `max_fails` at all. The
difference between the two columns is therefore run-to-run noise, and it is large: treat single-run
differences below about 2× as unproven.

### Noise and method

- Runs 1 and 2 of the unchanged code at 100 req/s gave search p99 46 ms and 490 ms.
- One further run was discarded: the host's load average was 125 during it (an IDE was re-indexing after a
  build), and every phase failed with the application containers above their CPU caps. Since then every run
  waits for a load average below 6 and records the load while it runs.

## Round 1: Flyway schema, before the `ddl-auto` switch

### Summary

- **No double booking in any run.** 8 verified runs, 27,369 confirmed bookings, 0 overlapping bay or
  technician bookings, and exactly one winner per contended slot.
- **But under overload, some clients never learn that they booked.** In the runs past the knee, the
  database holds more bookings than k6 received `201`s for (3,374 vs 3,369; 729 vs 723; 5,696 vs 5,483 in a
  first, more overloaded run). Those bookings committed after nginx had already returned `504` to the client.
  The design covers this case (sending the same request again returns the booking), but only if
  clients do retry. k6 did not.
- **Capacity: about 100 req/s** (≈ 90 searches + 9 bookings per second) with 2 replicas before booking p99
  goes over 500 ms. Beyond that, latency climbs to seconds.
- **The database is the bottleneck, not the app.** At 150 req/s Postgres sits at its 2-CPU cap while each
  replica uses about 35 % of one CPU. One query, the technician-and-shift lookup, takes **about 95 % of
  database time**. It scans the shifts of every technician in every dealership, so its cost grows with the
  number of dealerships, not with the load on one dealership. A tested rewrite makes it about 5.5× cheaper
  (see [Bottleneck](#bottleneck-one-query)).
- **A second replica helps** (knee 75–100 → 100–150 req/s), because it doubles the database connections
  (2 × 10). **A bigger pool hurts**: 2 × 20 connections failed earlier than 2 × 10, since more connections
  only add contention on 2 database CPUs.
- **Overload is not handled as fail-fast as documented.** Past the knee, requests queue for up to nginx's
  10 s timeout and come back as `504`, not `503` within about 2 s. `nginx max_fails=1` then takes a replica
  out after a single timeout. When both are out, **every** request gets an instant `502` for 5 s.
- Soak (60 req/s, 30 min): no leak (heap flat at about 50 MB), but one unexplained slow period of about 10
  minutes (see [Soak](#soak-60-reqs-for-30-minutes)).

### Setup

| | |
|---|---|
| Host | Intel i5-1038NG7 (4 cores / 8 threads), 16 GB, macOS; Docker in a colima VM with 7 CPU / 12 GiB |
| Caps | app 2 CPU / 1.5 GB each, Postgres 2 CPU / 2 GB (`shared_buffers=512MB`), k6 1 CPU, nginx 0.5 CPU |
| Data | 50 dealerships × (8 general + 2 alignment bays, 10 technicians), shifts for 90 days, 2,000 vehicles; plus one "hot" dealership with a single alignment bay |
| Load | Open model (arrival rate). Each iteration searches availability for a random dealership, service and day; 1 in 10 then books a random free slot from the result |
| Pass criteria per phase | search p95 < 200 ms, booking p99 < 500 ms (the alert level in design §9), < 1 % failed requests; `409` is a valid answer |

Every run starts from an empty database. Absolute numbers depend on this laptop, so the useful results are
the comparisons between runs.

### Ramp: 1 vs 2 replicas, pool 10 vs 20

60 s per level. "Achieved" counts every HTTP request, searches and bookings.

| Target req/s | 2 replicas, pool 10 | 2 replicas, pool 20 | 1 replica, pool 10 |
|---|---|---|---|
| 25 | pass: search p95 24 ms, book p99 149 ms (JVM warm-up) | pass | pass |
| 50 | pass: 14 / 29 ms | pass: 22 / 78 ms | pass: 28 / 72 ms |
| 75 | pass: 12 / 21 ms | pass: 26 / 52 ms | pass: 24 / 112 ms |
| 100 | **pass**: 12 / 22 ms | **fail**: search p99 341, book p99 591 ms | **fail**: book p99 1,088 ms, 23 waiting for a connection |
| 150 | **fail**: search p95 2.1 s, 40 waiting for a connection | fail: p95 2.9 s | fail: p95 5.8 s, **802 × 504** (9.6 %) |
| 200 | fail: p95 4.5 s | fail: p95 5.2 s, **687 × 502** (6.4 %) | fail: **1,993 × 504** (17 %) |

**Knee:** 100–150 req/s with 2 replicas and pool 10; 75–100 req/s for the other two configurations.
100 req/s is borderline, not a safe operating point: the same level failed when it was the first phase after
a cold start.

Average container CPU per phase, 2 replicas, pool 10 (100 = one CPU):

| Target req/s | 25 | 50 | 75 | 100 | 150 | 200 |
|---|---|---|---|---|---|---|
| Postgres (cap 200) | 25 | 41 | 59 | 79 | **172** | **212** |
| app1 / app2 (cap 200 each) | 25 / 25 | 16 / 21 | 20 / 20 | 20 / 20 | 38 / 34 | 34 / 38 |

With 1 replica, the limit at 100 req/s is the 10 connections of its single pool, while Postgres is at 93 %.
With 2 replicas and 20 connections in total, it is Postgres CPU. With 40 connections, Postgres reached its
cap one level earlier (110 % at 100 req/s against 79 % with 20 connections).

### Bottleneck: one query

`pg_stat_statements` over 60 s at 100 req/s (2 replicas, pool 10):

| Statement | Calls | Mean | Total DB time |
|---|---|---|---|
| technician + skill + **shift** (availability, `AvailabilityRepository.technicians`) | 5,890 | 57 ms | 336 s |
| technician + skill + **shift** (booking, `CandidateRepository.freeTechnicians`) | 584 | 54 ms | 31 s |
| bays with busy slots (availability) | 5,890 | 0.5 ms | 3 s |
| everything else (13 statements) | | < 3 ms | 7 s |

The two shift lookups account for about 95 % of database time. Run alone, the availability version takes 6 ms
and reads 377 buffers. The plan scans the shift index on the time range only, which returns the shifts of
**all 502 technicians** for that day, and only then joins to the 10 technicians of the requested
dealership:

```
Hash Join
  -> Index Only Scan using technician_shift_no_overlap on technician_shift   (rows=502, 352 buffers)
       Index Cond: (shift && '[day 08:00, day 18:00)')
  -> Seq Scan on technician  Filter: dealership_id = 7                        (rows=10)
```

Tested fixes, each inside a rolled-back transaction:

| Variant | Time | Buffers | Cost grows with |
|---|---|---|---|
| Current plan | 6.3 ms | 377 | technicians of **all** dealerships |
| Extra btree or GiST index | 4.8–6.3 ms | 347 | the planner keeps the same plan |
| Nested loop looking up shifts **per technician** (`technician_id = ? AND shift && ?` on the existing exclusion index) | **1.1 ms** | **89** | technicians of **one** dealership |

The fix is a query change, not an index. Select the dealership's qualified technicians first, then fetch
shifts `WHERE technician_id IN (:ids) AND shift && :window`, the way `AvailabilityRepository` already fetches
busy slots. `freeTechnicians` needs the same change. Not implemented in this round.

### Overload behaviour

The design says overload returns `503` with `Retry-After` within about 2 s. Measured past the knee:

- `503` responses were rare (0–13 per phase). Most failures were `504`: requests waited until nginx's
  `proxy_read_timeout` (10 s). A search runs 5 statements, and **each one** borrows a connection separately
  with its own 2 s `connection-timeout`, so the request as a whole has no time bound. Tomcat also queues
  requests in front of the pool (50 threads, accept queue of 100).
- A `504` does not mean the booking failed. The replica keeps working after nginx gives up and can still
  commit, as the booking counts above show. A client that treats `504` as "not booked" and does not send
  the same request again leaves a booking behind that it does not know about.
- The `502`s, reproduced in a dedicated 200 req/s run: nginx logged 94 `upstream timed out`, then 583
  `no live upstreams`. With `max_fails=1 fail_timeout=5s` and `proxy_next_upstream error timeout`, one slow
  response takes a replica out for 5 s. When both are out, every request fails instantly, including cheap
  ones. Tomcat never refused a connection (0 `Connection refused`).

### Hot slot: 50 requests/s for one slot, 180 s

| Result | Value |
|---|---|
| Slots won | 177, each by exactly 1 request (verified in SQL) |
| Losers | 8,824 × `409`, 0 × `503`, 0 × `5xx` |
| Booking latency | p95 19 ms, p99 52 ms |
| Races lost at the constraint | 38 (`scheduler_booking_conflicts_total{resource="bay"}`); the rest were rejected from the candidate query without locking |
| Postgres CPU | 38 % average |

Contention on one resource is cheap and clean. The advisory-lock design holds up as intended.

### Soak: 60 req/s for 30 minutes

| Window (min) | 0–5 | 5–10 | 10–15 | 15–20 | 20–25 | 25–30 |
|---|---|---|---|---|---|---|
| Search p99 ms | 41 | 28 | 77 | 254 | **771** | 22 |
| Booking p99 ms | 87 | 43 | 90 | 304 | **893** | 28 |
| Max waiting for a connection | 0 | 0 | 0 | 11 | 21 | 0 |
| Heap max MB | 49 | 49 | 49 | 49 | 51 | 50 |

10,879 bookings, 2 × `503`, no other errors, no leak. Between minutes 15 and 25, latency rose and then
recovered by itself. Postgres averaged 55–85 % CPU in that window (132 % in one minute), checkpoints were
small (about 500 buffers), and the heap was flat. **The cause is not identified.** Candidates are
interference on the host (laptop VM) or a plan change after one of the 18 auto-analyzes of `appointment`.
Confirming it needs a rerun with `pg_stat_statements` snapshots per window.

## Recommendations and status

1. **Rewrite the two technician-shift queries** to look up shifts per technician. *Status:* the booking
   query is rewritten (round 2). The availability query already gets that plan on the current schema and was
   left unchanged.
2. **Bound the whole request, not each connection borrow.** For example, run a search in one read-only
   transaction (one connection, `statement_timeout` for the whole unit), and cap Tomcat's queue so the
   service answers `503` before nginx answers `504`. *Status:* open.
3. **Document the client contract for `502`/`504`/timeouts.** Send the same request again until
   the answer is definitive. *Status:* done; the `POST /appointments` description in the OpenAPI contract says
   so, and the E2E test exercises it.
4. **nginx: `max_fails=3` or more**, so that one slow request does not eject a healthy replica. *Status:*
   done in `docker/nginx.conf` and the load-test configs; not re-verified under load (round 2).
5. **Keep the pool small.** About 20 connections in total for a 2-CPU Postgres was the best of the
   configurations measured. Add PgBouncer before adding replicas beyond that. *Status:* unchanged advice.
6. `statement_timeout` applied to Flyway migrations. *Status:* obsolete; Flyway is gone, and
   `db/constraints.sql` sets `SET LOCAL statement_timeout = 0` for itself.

## Limitations

- Single machine. k6, both replicas and Postgres share 4 physical cores, and the CPU caps add up to 7.5 on a
  7-CPU VM. k6 dropped iterations in the overloaded runs, when all its virtual users were waiting on slow
  responses, and 86 during the soak. Its CPU averaged 13–43 % of its 1-CPU cap but touched the cap briefly in two overloaded runs.
- 60 s per level is enough to find the knee, not to prove long-term stability at it.
- UTC-only load data; time-zone logic is covered by the integration tests, not here.
- The load mix (10 % bookings, uniform over 50 dealerships and 40 days) is an assumption, not production data.
