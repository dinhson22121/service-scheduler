import {
    ALIGNMENT, HOT_DEALERSHIP, TREND_STATS, book, phaseSummary, phaseThresholds, recordServerDeltas,
    serverSnapshot, workingDay,
} from './lib.js';

const RATE = Number(__ENV.RATE || 50);
const DURATION_SECONDS = Number(__ENV.DURATION_SECONDS || 180);
const SLOTS_PER_DAY = 6;
const SLOT_MINUTES = 90;
const PHASES = ['hot'];

export const options = {
    summaryTrendStats: TREND_STATS,
    thresholds: phaseThresholds(PHASES),
    scenarios: {
        race: {
            executor: 'constant-arrival-rate',
            rate: RATE,
            timeUnit: '1s',
            duration: `${DURATION_SECONDS}s`,
            preAllocatedVUs: RATE * 2,
            maxVUs: RATE * 8,
        },
    },
};

function slotStart(k) {
    const minutes = 8 * 60 + (k % SLOTS_PER_DAY) * SLOT_MINUTES;
    const hh = String(Math.floor(minutes / 60)).padStart(2, '0');
    const mm = String(minutes % 60).padStart(2, '0');
    return `${workingDay(Math.floor(k / SLOTS_PER_DAY))}T${hh}:${mm}:00Z`;
}

export function setup() {
    return { startedAt: Date.now(), before: serverSnapshot() };
}

export default function (data) {
    const k = Math.floor((Date.now() - data.startedAt) / 1000);
    book(HOT_DEALERSHIP, ALIGNMENT, slotStart(k), { phase: 'hot' });
}

export function teardown(data) {
    recordServerDeltas(data.before);
}

export function handleSummary(data) {
    return phaseSummary(data, `Hot slot, ${RATE} requests/s per slot for ${DURATION_SECONDS}s`, PHASES,
        DURATION_SECONDS, () => `${RATE}/s on one slot`);
}
