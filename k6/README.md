# k6 tests

This directory contains load tests for `disa-returns-backend`.

## Run

```bash
k6 run -e BASE_URL=http://localhost:1207 k6/upscan-callback.js
```

Optional environment variables:

- `BASE_URL`: backend URL, default `http://localhost:1207`
- `VUS`: virtual users, default `5`
- `DURATION`: test duration, default `30s`

The script sends `POST /disa-returns-backend/upscan-callback?filename=...` with unique filenames that include:

- `-xs`
- `-s`
- `-m`
- `-l`
- `-xl`
