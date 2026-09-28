import http from 'k6/http';
import exec from 'k6/execution';
import { Counter, Trend } from 'k6/metrics';

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
export const RUN_LABEL = __ENV.RUN_LABEL || 'run';
const ACTUATORS = (__ENV.ACTUATORS || 'http://localhost:8081').split(',');

export const DEALERSHIPS = 50;
export const HOT_DEALERSHIP = 51;
export const VEHICLES = 2000;
export const ALIGNMENT = 3;
const SERVICE_TYPES = [1, 2, 3, 4];
const FIRST_DAY_AHEAD = 2;

http.setResponseCallback(http.expectedStatuses(200, 201, 409));

export const TREND_STATS = ['count', 'avg', 'med', 'p(95)', 'p(99)', 'max'];

const booked = new Counter('booked');
const rejected = new Counter('rejected_409');
const overloaded = new Counter('overloaded_503');
const badGateway = new Counter('bad_gateway_502');
const gatewayTimeout = new Counter('gateway_timeout_504');
const serverErrors = new Counter('server_errors');
const hikariPending = new Trend('hikari_pending');
const hikariActive = new Trend('hikari_active');
const heapMb = new Trend('heap_used_mb');
const appCpu = new Trend('app_cpu_pct');

export function randomInt(min, max) {
    return min + Math.floor(Math.random() * (max - min + 1));
}

function pick(list) {
    return list[randomInt(0, list.length - 1)];
}

export function workingDay(n) {
    const date = new Date();
    date.setUTCHours(0, 0, 0, 0);
    date.setUTCDate(date.getUTCDate() + FIRST_DAY_AHEAD);
    let remaining = n;
    while (date.getUTCDay() === 0 || remaining > 0) {
        if (date.getUTCDay() !== 0) {
            remaining--;
        }
        date.setUTCDate(date.getUTCDate() + 1);
    }
    return date.toISOString().slice(0, 10);
}

export function phaseAt(phases, phaseSeconds) {
    const elapsed = (Date.now() - exec.scenario.startTime) / 1000;
    return phases[Math.min(Math.floor(elapsed / phaseSeconds), phases.length - 1)];
}

export function availability(dealershipId, serviceTypeId, date, tags) {
    return http.get(
        `${BASE_URL}/api/v1/dealerships/${dealershipId}/availability?serviceTypeId=${serviceTypeId}&date=${date}`,
        { tags: { ...tags, name: 'availability' } });
}

export function book(dealershipId, serviceTypeId, startTime, tags) {
    const vehicleId = randomInt(1, VEHICLES);
    const res = http.post(`${BASE_URL}/api/v1/appointments`, JSON.stringify({
        dealershipId, customerId: vehicleId, vehicleId, serviceTypeId, startTime,
    }), {
        headers: { 'Content-Type': 'application/json' },
        tags: { ...tags, name: 'book' },
    });
    countOutcome(res, tags);
    return res;
}

function countOutcome(res, tags) {
    if (res.status === 201) {
        booked.add(1, tags);
    } else if (res.status === 409) {
        rejected.add(1, tags);
    } else if (res.status === 503) {
        overloaded.add(1, tags);
    } else if (res.status === 502) {
        badGateway.add(1, tags);
    } else if (res.status === 504) {
        gatewayTimeout.add(1, tags);
    } else {
        serverErrors.add(1, tags);
    }
}

function searchOutcome(res, tags) {
    if (res.status !== 200) {
        countOutcome(res, tags);
    }
}

export function searchAndMaybeBook(tags, bookRatio) {
    const dealershipId = randomInt(1, DEALERSHIPS);
    const serviceTypeId = pick(SERVICE_TYPES);
    const res = availability(dealershipId, serviceTypeId, workingDay(randomInt(0, 40)), tags);
    searchOutcome(res, tags);
    if (res.status !== 200 || Math.random() >= bookRatio) {
        return;
    }
    const slots = res.json('slots');
    if (slots.length > 0) {
        book(dealershipId, serviceTypeId, pick(slots).startTime, tags);
    }
}

function scrape(actuator) {
    const res = http.get(`${actuator}/actuator/prometheus`, { tags: { name: 'probe' } });
    return res.status === 200 ? res.body : '';
}

function sample(body, metric, labelFilter = '') {
    let sum = 0;
    for (const line of body.split('\n')) {
        if (line.startsWith(metric) && (line[metric.length] === '{' || line[metric.length] === ' ')
            && line.includes(labelFilter)) {
            sum += parseFloat(line.substring(line.lastIndexOf(' ') + 1));
        }
    }
    return sum;
}

export function probe(tags) {
    for (const actuator of ACTUATORS) {
        const body = scrape(actuator);
        if (!body) {
            continue;
        }
        const t = { ...tags, instance: actuator };
        hikariPending.add(sample(body, 'hikaricp_connections_pending'), t);
        hikariActive.add(sample(body, 'hikaricp_connections_active'), t);
        heapMb.add(sample(body, 'jvm_memory_used_bytes', 'area="heap"') / 1024 / 1024, t);
        appCpu.add(sample(body, 'process_cpu_usage') * 100, t);
    }
}

const SERVER_COUNTERS = {
    server_confirmed: ['scheduler_bookings_total', 'outcome="confirmed"'],
    server_no_capacity: ['scheduler_bookings_total', 'outcome="no_capacity"'],
    server_contention_exhausted: ['scheduler_bookings_total', 'outcome="contention_exhausted"'],
    server_bay_conflicts: ['scheduler_booking_conflicts_total', 'resource="bay"'],
    server_technician_conflicts: ['scheduler_booking_conflicts_total', 'resource="technician"'],
    server_pool_timeouts: ['hikaricp_connections_timeout_total', ''],
};
const serverDeltas = Object.fromEntries(Object.keys(SERVER_COUNTERS).map(name => [name, new Counter(name)]));

export function serverSnapshot() {
    const bodies = ACTUATORS.map(scrape);
    return Object.fromEntries(Object.entries(SERVER_COUNTERS).map(([name, [metric, filter]]) =>
        [name, bodies.reduce((sum, body) => sum + sample(body, metric, filter), 0)]));
}

export function recordServerDeltas(before) {
    const after = serverSnapshot();
    for (const name of Object.keys(SERVER_COUNTERS)) {
        serverDeltas[name].add(after[name] - before[name]);
    }
}

export function phaseThresholds(phases) {
    const thresholds = {};
    for (const phase of phases) {
        thresholds[`http_req_duration{name:availability,phase:${phase}}`] = ['p(95)<200'];
        thresholds[`http_req_duration{name:book,phase:${phase}}`] = ['p(99)<500'];
        thresholds[`http_req_failed{phase:${phase}}`] = ['rate<0.01'];
        for (const metric of ['http_reqs', 'booked', 'rejected_409', 'overloaded_503', 'bad_gateway_502',
            'gateway_timeout_504', 'server_errors']) {
            thresholds[`${metric}{phase:${phase}}`] = ['count>=0'];
        }
        for (const metric of ['hikari_pending', 'hikari_active', 'heap_used_mb', 'app_cpu_pct']) {
            thresholds[`${metric}{phase:${phase}}`] = ['max>=0'];
        }
    }
    for (const name of Object.keys(SERVER_COUNTERS)) {
        thresholds[name] = ['count>=0'];
    }
    thresholds['dropped_iterations'] = ['count>=0'];
    return thresholds;
}

function value(data, key, stat) {
    const metric = data.metrics[key];
    if (!metric || (metric.type === 'trend' && !metric.values.count)) {
        return undefined;
    }
    return metric.values[stat];
}

function passed(data, key) {
    const metric = data.metrics[key];
    return !metric || !metric.thresholds || Object.values(metric.thresholds).every(t => t.ok);
}

function fmt(v, digits = 0) {
    return v === undefined || Number.isNaN(v) ? '-' : v.toFixed(digits);
}

export function phaseSummary(data, title, phases, phaseSeconds, describePhase) {
    const rows = phases.map(phase => {
        const q = `{phase:${phase}}`;
        const avail = `http_req_duration{name:availability,phase:${phase}}`;
        const bookKey = `http_req_duration{name:book,phase:${phase}}`;
        const ok = passed(data, avail) && passed(data, bookKey) && passed(data, `http_req_failed${q}`);
        const count = key => value(data, `${key}${q}`, 'count');
        return `| ${describePhase(phase)} | ${fmt(count('http_reqs') / phaseSeconds)} `
            + `| ${fmt(value(data, avail, 'p(95)'))} | ${fmt(value(data, avail, 'p(99)'))} `
            + `| ${fmt(value(data, bookKey, 'p(95)'))} | ${fmt(value(data, bookKey, 'p(99)'))} `
            + `| ${fmt(count('booked'))} | ${fmt(count('rejected_409'))} | ${fmt(count('overloaded_503'))} `
            + `| ${fmt(count('bad_gateway_502'))} | ${fmt(count('gateway_timeout_504'))} | ${fmt(count('server_errors'))} `
            + `| ${fmt(value(data, `http_req_failed${q}`, 'rate') * 100, 2)} `
            + `| ${fmt(value(data, `hikari_pending${q}`, 'max'))} | ${fmt(value(data, `app_cpu_pct${q}`, 'max'))} `
            + `| ${fmt(value(data, `heap_used_mb${q}`, 'max'))} | ${ok ? 'pass' : '**FAIL**'} |`;
    });
    const server = Object.keys(SERVER_COUNTERS)
        .map(name => `| ${name} | ${fmt(value(data, name, 'count'))} |`).join('\n');
    const report = `## ${title} (${RUN_LABEL})

| Phase | Achieved req/s | Search p95 ms | Search p99 ms | Book p95 ms | Book p99 ms | 201 | 409 | 503 | 502 | 504 | Other errors | Failed % | Pool pending max | App CPU max % | Heap max MB | Verdict |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
${rows.join('\n')}

503 = the app refused fast; 502/504 = nginx gave up on a replica (connection refused / 10 s read timeout);
other errors include k6 client timeouts (status 0).

Dropped iterations (k6 could not start them on time): ${fmt(value(data, 'dropped_iterations', 'count'))}

| Server counter (delta, all replicas) | Value |
|---|---|
${server}
`;
    const file = `/results/${RUN_LABEL}`;
    return {
        stdout: report,
        [`${file}.md`]: report,
        [`${file}.json`]: JSON.stringify(data, null, 1),
    };
}
