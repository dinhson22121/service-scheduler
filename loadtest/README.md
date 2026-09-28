# Load tests (k6)

Measures where the stack stops meeting its latency targets, whether a second replica raises that
limit, how booking behaves under sustained contention, and whether anything drifts over time.
Results and their interpretation: [`docs/loadtest-report.md`](../docs/loadtest-report.md).

## Run

Prerequisites: Docker with Compose. k6 runs in a container (`grafana/k6:1.3.0`); nothing else to install.

```bash
docker compose build app1                                   # once; the runs reuse service-scheduler:local

loadtest/run.sh mixed-ramp ramp-2r                          # 2 replicas, the default
REPLICAS=1 loadtest/run.sh mixed-ramp ramp-1r               # same load, one replica
DB_POOL_SIZE=20 loadtest/run.sh mixed-ramp ramp-2r-pool20   # Hikari pool size per replica
loadtest/run.sh hot-slot hot                                # sustained contention on one bay
RATE=60 MINUTES=30 loadtest/run.sh soak soak                # the round-1 soak rate, held for 30 minutes
```

Each run starts from an empty database, so runs are comparable. It writes to `loadtest/results/` (git-ignored):
`<label>.md` (per-phase table), `<label>.json` (full k6 summary), `<label>-stats.csv` (container CPU and
memory every few seconds), `<label>-verify.txt` (correctness checks) and `<label>-env.txt`.

Knobs: `LEVELS` and `STEP_SECONDS` (mixed-ramp), `RATE` and `DURATION_SECONDS` (hot-slot), `RATE` and
`MINUTES` (soak), `MAX_VUS`. k6 exits with 99 when a phase misses its thresholds, which is the expected way for a
ramp to find the knee, so `run.sh` still exits 0; `STRICT=1` makes it exit with k6's code instead (used by CI).
A correctness violation always makes `run.sh` exit 1.

## What each script does

| Script | Load | Question it answers |
|---|---|---|
| `mixed-ramp.js` | Open model. The arrival rate steps through `LEVELS`, holding each for `STEP_SECONDS`. Every iteration searches availability; 1 in 10 then books a random free slot from the result | At which rate does the first phase miss its targets (the knee)? |
| `hot-slot.js` | `RATE` booking requests per second, all for the same slot at a dealership with one alignment bay; a new slot each second | Under sustained contention, do the losers get a fast `409` rather than `503`/timeouts, and does each slot get exactly one winner? |
| `soak.js` | Constant `RATE` of the same mix, reported in 5-minute windows | Do latency, heap or pool waits creep up over time? |

A phase **passes** when search p95 < 200 ms, booking p99 < 500 ms (the alert level in design §9) and fewer
than 1 % of requests fail. `409` counts as a correct answer, not a failure.

## How to read the results

- **Dropped iterations > 0**: k6 itself could not keep up (it is capped at 1 CPU). Numbers from that phase
  on measure the load generator, not the service.
- **Pool pending max > 0**: requests queued for a database connection; the pool or Postgres is the limit.
- **`-stats.csv`**: whichever container sits at its CPU cap (app 200, postgres 200) first is the bottleneck.
- **verify**: every `must be 0` row must be 0. `confirmed_total` should equal the k6 `201` count.

## Environment and limits

Resource caps (`docker-compose.loadtest.yml`): each app 2 CPU / 1.5 GB, Postgres 2 CPU / 2 GB with
`shared_buffers=512MB` and `pg_stat_statements` preloaded (run `CREATE EXTENSION pg_stat_statements` to profile),
k6 1 CPU, nginx 0.5 CPU. The caps add up to 7.5 on a 7-CPU VM on purpose: they are ceilings, not reservations,
and not every container peaks at the same moment. The load-test nginx config differs from `docker/nginx.conf` only in worker limits and
access logging.

Everything shares one machine, so absolute numbers depend on the host. Compare runs with each other
(1 vs 2 replicas, pool 10 vs 20), not with production. The seed (`loadtest/seed/loadtest_seed.sql`, passed to the app as `SEED_LOCATIONS`) runs all dealerships
in UTC to keep the scripts simple; time zones are covered by the integration tests.
