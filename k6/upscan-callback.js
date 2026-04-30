import http from 'k6/http';
import { check, sleep } from 'k6';

const sizeMarkers = ['-xs', '-s', '-m', '-l', '-xl', '-2xl'];
const allowedTypes = new Set(['csv', 'xlsx']);

export const options = {
  vus: __ENV.VUS ? Number(__ENV.VUS) : 5,
  duration: __ENV.DURATION || '30s',
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

function buildFilename(marker, type) {
  const uniquePart = `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  return `k6-upscan-${marker}-${uniquePart}.${type}`;
}

function pickMarker(iteration) {
  return sizeMarkers[iteration % sizeMarkers.length];
}

export default function () {
  const iteration = __ITER;
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
