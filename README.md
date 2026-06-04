
# disa-returns-backend


### Before running the app

This repository relies on having mongodb running locally. You can start it with:

```bash
# first check to see if mongo is already running
docker ps | grep mongodb

# if not, start it
docker run --restart unless-stopped --name mongodb -p 27017:27017 -d percona/percona-server-mongodb:7.0 --replSet rs0
```

Reference instructions for [setting up docker](https://docs.tax.service.gov.uk/mdtp-handbook/documentation/developer-set-up/install-docker.html) and [running mongodb](https://docs.tax.service.gov.uk/mdtp-handbook/documentation/developer-set-up/set-up-mongodb.html#install-mongodb-applesilicon-mac).

## Running the app

### Service manager
The whole service can be started with:
```bash
sm2 --start DISA_RETURNS_ALL
```

### Locally

```bash
sbt run
```

## Upscan callback endpoint

Upscan posts final upload scan results to:

```text
POST /disa-returns-backend/monthly/upscan/callback/:zReference/:taxYear/:month
```

Route parameters:

| Parameter | Description |
| --- | --- |
| `zReference` | DISA ISA manager reference for the monthly return journey |
| `taxYear` | Tax year for the monthly return journey |
| `month` | Month for the monthly return journey |

The endpoint accepts Upscan `READY` and `FAILED` callback payloads and returns `202 Accepted` when the payload is valid. Invalid JSON, unknown `fileStatus` values, or unknown `failureReason` values return `400 Bad Request`. Non-JSON requests return `415 Unsupported Media Type`.

Successful upload callback example:

```json
{
  "reference": "2b4d6f3a-8c1e-4e4b-9c7a-123456789abc",
  "downloadUrl": "https://fus-outbound-bucket.s3.eu-west-2.amazonaws.com/object-key?X-Amz-Signature=...",
  "fileStatus": "READY",
  "uploadDetails": {
    "fileName": "return.csv",
    "fileMimeType": "text/csv",
    "uploadTimestamp": "2026-05-17T12:00:00Z",
    "checksum": "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
    "size": 987
  }
}
```

Failed upload callback example:

```json
{
  "reference": "2b4d6f3a-8c1e-4e4b-9c7a-123456789abc",
  "fileStatus": "FAILED",
  "failureDetails": {
    "failureReason": "REJECTED",
    "message": "MIME type [application/zip] is not allowed for service: [disa-returns-frontend]"
  }
}
```

Supported failure reasons are `QUARANTINE`, `REJECTED`, and `UNKNOWN`.

You can then query the app to ensure it is working with the following command:

```bash
# other useful commands
sbt clean

sbt reload

sbt compile
```

### Running the test suite

To run the unit tests:

```bash
sbt test
```

To run the integration tests:

```bash
sbt it/test
```

### Before you commit

This service leverages scalaFmt to ensure that the code is formatted correctly.

Before you commit, please run the following commands to check that the code is formatted correctly:

```bash
# runs a scala format check, runs unit tests, runs integration tests and produces a coverage report.
sbt runAllChecks

# checks all source and sbt files are correctly formatted
sbt prePrChecks

# if checks fail, you can format with the following commands

# formats all source files
sbt scalafmtAll

# formats all sbt files
sbt scalafmtSbt

# formats just the main source files (excludes test and configuration files)
sbt scalafmt
```

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
