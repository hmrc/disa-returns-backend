# k6 tests

This directory contains load tests for `disa-returns-backend`.

## Run

```bash
k6 run -e BASE_URL=http://localhost:1207 k6/upscan-callback.js
```

Optional environment variables:

- `BASE_URL`: backend URL, default `http://localhost:1207`
- `TYPE`: file type to simulate, either `csv` or `xlsx` (default `csv`)
- `VUS`: virtual users, default `5`
- `DURATION`: test duration, default `30s`

The script sends `POST /disa-returns-backend/upscan-callback?filename=...` with unique filenames that include a size marker and extension:

- `-xs`
- `-s`
- `-m`
- `-l`
- `-xl`
- `-2xl`

Example:

```bash
k6 run -e BASE_URL=http://localhost:1207 -e TYPE=xlsx k6/upscan-callback.js
```
