import http from 'k6/http';
import {check, sleep} from 'k6';
import exec from 'k6/execution';

const sizeMarkers = ['xs', 's', 'm', 'l', 'xl', '2xl'];
const allowedTypes = new Set(['csv', 'xlsx']);

const vus = __ENV.VUS ? Number(__ENV.VUS) : 5;
const iterations = __ENV.ITERATIONS ? Number(__ENV.ITERATIONS) : 150;
const maxDuration = __ENV.MAX_DURATION || '10m';

if (!Number.isInteger(vus) || vus <= 0) {
    throw new Error(`VUS must be a positive integer. Received: ${__ENV.VUS}`);
}

if (!Number.isInteger(iterations) || iterations <= 0) {
    throw new Error(`ITERATIONS must be a positive integer. Received: ${__ENV.ITERATIONS}`);
}

if (iterations % sizeMarkers.length !== 0) {
    throw new Error(
        `ITERATIONS must be divisible by ${sizeMarkers.length} so each size marker is used equally. ` +
        `Received: ${iterations}`
    );
}

export const options = {
    scenarios: {
        upscan_callbacks: {
            executor: 'shared-iterations',
            vus,
            iterations,
            maxDuration
        }
    },
    thresholds: {
        http_req_failed: ['rate<0.01'],
        http_req_duration: ['p(95)<1000']
    }
};

function resolveType(rawType) {
    const type = (rawType || 'csv').toLowerCase();

    if (!allowedTypes.has(type)) {
        throw new Error(`Unsupported type: ${rawType}`);
    }

    return type;
}

function pickMarker(iterationInTest) {
    return sizeMarkers[iterationInTest % sizeMarkers.length];
}

function buildFilename(marker, type) {
    const uniquePart = `${Date.now()}-${Math.random().toString(16).slice(2)}`;
    return `k6-upscan--${marker}-${uniquePart}.${type}`;
}

export default function () {
    const iteration = exec.scenario.iterationInTest;
    const marker = pickMarker(iteration);
    const type = resolveType(__ENV.TYPE);
    const filename = buildFilename(marker, type);

    const baseUrl = __ENV.BASE_URL || 'http://localhost:1207';
    const url = `${baseUrl}/disa-returns-backend/upscan-callback?filename=${encodeURIComponent(filename)}`;

    const response = http.post(url, null, {
        headers: {
            'Content-Type': 'application/json'
        }
    });

    check(response, {
        'upscan callback returned 200': (r) => r.status === 200
    });

    sleep(1);
}
