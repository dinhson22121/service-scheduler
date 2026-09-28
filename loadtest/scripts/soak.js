import { sleep } from 'k6';
import {
    TREND_STATS, phaseAt, phaseSummary, phaseThresholds, probe, recordServerDeltas, searchAndMaybeBook,
    serverSnapshot,
} from './lib.js';

const RATE = Number(__ENV.RATE || 200);
const MINUTES = Number(__ENV.MINUTES || 30);
const WINDOW_MINUTES = 5;
const BOOK_RATIO = 0.1;
const PHASES = Array.from({ length: Math.ceil(MINUTES / WINDOW_MINUTES) }, (_, i) => `w${i * WINDOW_MINUTES}`);

export const options = {
    summaryTrendStats: TREND_STATS,
    thresholds: phaseThresholds(PHASES),
    scenarios: {
        load: {
            executor: 'constant-arrival-rate',
            exec: 'load',
            rate: RATE,
            timeUnit: '1s',
            duration: `${MINUTES}m`,
            preAllocatedVUs: 50,
            maxVUs: 600,
        },
        probe: {
            executor: 'constant-vus',
            exec: 'serverProbe',
            vus: 1,
            duration: `${MINUTES}m`,
        },
    },
};

export function setup() {
    return serverSnapshot();
}

export function load() {
    searchAndMaybeBook({ phase: phaseAt(PHASES, WINDOW_MINUTES * 60) }, BOOK_RATIO);
}

export function serverProbe() {
    probe({ phase: phaseAt(PHASES, WINDOW_MINUTES * 60) });
    sleep(5);
}

export function teardown(before) {
    recordServerDeltas(before);
}

export function handleSummary(data) {
    return phaseSummary(data, `Soak, ${RATE} req/s for ${MINUTES} min`, PHASES, WINDOW_MINUTES * 60,
        phase => `minute ${phase.substring(1)}-${Number(phase.substring(1)) + WINDOW_MINUTES}`);
}
