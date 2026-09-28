import { sleep } from 'k6';
import {
    TREND_STATS, phaseAt, phaseSummary, phaseThresholds, probe, recordServerDeltas, searchAndMaybeBook,
    serverSnapshot,
} from './lib.js';

const LEVELS = (__ENV.LEVELS || '50,100,200,300,400,600,800,1000').split(',').map(Number);
const STEP_SECONDS = Number(__ENV.STEP_SECONDS || 60);
const RAMP_SECONDS = 5;
const BOOK_RATIO = 0.1;
const PHASES = LEVELS.map(level => `r${level}`);

export const options = {
    summaryTrendStats: TREND_STATS,
    thresholds: phaseThresholds(PHASES),
    scenarios: {
        load: {
            executor: 'ramping-arrival-rate',
            exec: 'load',
            startRate: LEVELS[0],
            timeUnit: '1s',
            preAllocatedVUs: 50,
            maxVUs: Number(__ENV.MAX_VUS || 600),
            stages: LEVELS.flatMap(level => [
                { target: level, duration: `${RAMP_SECONDS}s` },
                { target: level, duration: `${STEP_SECONDS - RAMP_SECONDS}s` },
            ]),
        },
        probe: {
            executor: 'constant-vus',
            exec: 'serverProbe',
            vus: 1,
            duration: `${LEVELS.length * STEP_SECONDS}s`,
        },
    },
};

export function setup() {
    return serverSnapshot();
}

export function load() {
    searchAndMaybeBook({ phase: phaseAt(PHASES, STEP_SECONDS) }, BOOK_RATIO);
}

export function serverProbe() {
    probe({ phase: phaseAt(PHASES, STEP_SECONDS) });
    sleep(5);
}

export function teardown(before) {
    recordServerDeltas(before);
}

export function handleSummary(data) {
    return phaseSummary(data, `Mixed ramp, ${STEP_SECONDS}s per level`, PHASES, STEP_SECONDS,
        phase => `${phase.substring(1)} req/s target`);
}
