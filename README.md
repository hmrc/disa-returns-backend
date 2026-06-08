
# disa-returns-backend

## Endpoints

| Method | Endpoint | Description |
| --- | --- | --- |
| `GET` | `/disa-returns-backend/monthly/:zReference/:taxYear/:month` | Gets an existing monthly return. |
| `GET` | `/disa-returns-backend/monthly/:zReference/:taxYear/:month/files/:reference` | Gets a file upload for a monthly return. |
| `DELETE` | `/disa-returns-backend/monthly/:zReference/:taxYear/:month/files/:reference` | Deletes a file upload from a monthly return. |
| `POST` | `/disa-returns-backend/monthly/:zReference/:taxYear/:month` | Creates a monthly return. |
| `POST` | `/disa-returns-backend/monthly/:zReference/:taxYear/:month/declarations` | Declares an existing monthly return. |
| `POST` | `/disa-returns-backend/monthly/:zReference/:taxYear/:month/files` | Creates a file upload placeholder for a monthly return. |
| `PUT` | `/disa-returns-backend/monthly/:zReference/:taxYear/:month/nilReturn` | Updates whether the monthly return is a nil return. |
| `POST` | `/disa-returns-backend/monthly/upscan/callback/:zReference/:taxYear/:month` | Receives the Upscan callback for a monthly return file upload. |

Path parameters:

- `zReference`: ISA manager reference in `Z1234` format. Lowercase values are accepted and normalised to uppercase.
- `taxYear`: Tax year in `2026-27` format.
- `month`: Month number from `1` to `12`, for example `5` for May.

Invalid path parameters return `400 Bad Request`.

### Create Monthly Return

`POST /disa-returns-backend/monthly/:zReference/:taxYear/:month`

Request body:

```json
{
  "nilReturn": false
}
```

- Returns `201 Created` with the resource path in the `Location` header when the monthly return is created.
- Returns `409 Conflict` when a monthly return already exists for the same `zReference`, `taxYear`, and `month`.
- Returns `503 Service Unavailable` when MongoDB is unavailable.
- A monthly return created with `"nilReturn": true` is created without file uploads.

### Declare Monthly Return

`POST /disa-returns-backend/monthly/:zReference/:taxYear/:month/declarations`

- Returns `200 OK` with the declared monthly return when the record exists and has not already been declared.
- Returns `409 Conflict` when the monthly return has already been declared.
- Returns `404 Not Found` when the monthly return does not exist.
- Returns `422 Unprocessable Entity` when the request is outside the declaration period.
- Returns `503 Service Unavailable` when MongoDB is unavailable.
- The declaration period is from 00:00 on the configured start day to the end of the configured end day, inclusive.

Example response:

```json
{
  "zReference": "Z1234",
  "taxYear": "2026-27",
  "month": 5,
  "nilReturn": false,
  "fileUploads": [],
  "createdOn": "2026-05-17T12:00:00Z",
  "declaredOn": "2026-05-17T12:00:00Z",
  "lastUpdated": "2026-05-17T12:00:00Z"
}
```

### Create File Upload

`POST /disa-returns-backend/monthly/:zReference/:taxYear/:month/files`

Request body:

```json
{
  "reference": "2b4d6f3a-8c1e-4e4b-9c7a-123456789abc"
}
```

- Returns `201 Created` with the file upload resource path in the `Location` header when the file upload placeholder is created.
- Returns `409 Conflict` when a file upload already exists with the same `reference`.
- Returns `404 Not Found` when the monthly return does not exist or cannot accept file uploads.
- Returns `422 Unprocessable Entity` when the monthly return has already been declared for the period.
- Returns `503 Service Unavailable` when MongoDB is unavailable.

### Get File Upload

`GET /disa-returns-backend/monthly/:zReference/:taxYear/:month/files/:reference`

- Returns `200 OK` with the file upload when the monthly return and file upload exist.
- Returns `404 Not Found` when the monthly return does not exist.
- Returns `404 Not Found` when the monthly return exists but the file upload does not exist.
- Returns `503 Service Unavailable` when MongoDB is unavailable.

Example response:

```json
{
  "reference": "2b4d6f3a-8c1e-4e4b-9c7a-123456789abc",
  "status": "CREATED",
  "createdOn": "2026-05-17T12:00:00Z"
}
```

### Delete File Upload

`DELETE /disa-returns-backend/monthly/:zReference/:taxYear/:month/files/:reference`

- Returns `204 No Content` when the monthly return and file upload exist. The file upload is removed from the monthly return.
- Returns `404 Not Found` when the monthly return does not exist or the file upload does not exist.
- Returns `503 Service Unavailable` when MongoDB is unavailable.

### Get Monthly Return

`GET /disa-returns-backend/monthly/:zReference/:taxYear/:month`

- Returns `200 OK` with the monthly return when the record exists.
- Returns `404 Not Found` when the monthly return does not exist.
- Returns `503 Service Unavailable` when MongoDB is unavailable.

Example response:

```json
{
  "zReference": "Z1234",
  "taxYear": "2026-27",
  "month": 5,
  "nilReturn": false,
  "fileUploads": [],
  "createdOn": "2026-05-17T12:00:00Z",
  "declaredOn": "2026-05-17T12:00:00Z",
  "lastUpdated": "2026-05-17T12:00:00Z"
}
```

### Update Nil Return

`PUT /disa-returns-backend/monthly/:zReference/:taxYear/:month/nilReturn`

Request body:

```json
{
  "value": true
}
```

- Returns `200 OK` with the updated monthly return when the record exists.
- Setting `value` to `true` removes all file uploads from the monthly return.
- Setting `value` to `false` updates `nilReturn` to `false` and leaves file uploads empty.
- Missing or non-boolean `value` fields return `400 Bad Request`.
- Returns `404 Not Found` when the monthly return does not exist.
- Returns `503 Service Unavailable` when MongoDB is unavailable.

### Monthly Upscan Callback

- The endpoint accepts Upscan `READY` and `FAILED` callback payloads and returns `202 Accepted` when the payload is valid.
- If the monthly return is a nil return, the callback still returns `202 Accepted` but the file result is not stored.
- If the monthly return is not a nil return, the callback stores the file result. Existing upload references are completed in place; new callback references are added as completed file uploads.
- Completed file uploads include `fileUploadDetails.upscanCompletedOn`, which records when Upscan processing completed, and `fileUploadDetails.upscanDownloadUrl` for the Upscan download URL.
- Invalid JSON, unknown `fileStatus` values, or unknown `failureReason` values return `400 Bad Request`.
- Non-JSON requests return `415 Unsupported Media Type`.

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
    "size": 1024
  }
}
```

Stored file upload after a successful callback:

```json
{
  "reference": "2b4d6f3a-8c1e-4e4b-9c7a-123456789abc",
  "status": "UPSCAN_SUCCESS",
  "createdOn": "2026-05-17T12:00:00Z",
  "fileUploadDetails": {
    "fileName": "return.csv",
    "fileMimeType": "text/csv",
    "uploadTimestamp": "2026-05-17T12:00:00Z",
    "checksum": "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
    "size": 1024,
    "upscanDownloadUrl": "https://fus-outbound-bucket.s3.eu-west-2.amazonaws.com/object-key?X-Amz-Signature=...",
    "upscanCompletedOn": "2026-05-17T12:01:00Z"
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

- Supported failure reasons are `QUARANTINE`, `REJECTED`, and `UNKNOWN`.

## Before running the app

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

To run locally with HMRC-style test-only routes enabled:

```bash
sbt run -Dapplication.router=testOnlyDoNotUseInAppConf.Routes
```

If starting through service-manager, pass the same JVM parameter in the local service profile:

```bash
-Dapplication.router=testOnlyDoNotUseInAppConf.Routes
```

Test-only clock routes are available only with that router:

- `GET /disa-returns-backend/test-only/clock`
- `PUT /disa-returns-backend/test-only/clock/yyyy-MM-dd`
- `DELETE /disa-returns-backend/test-only/clock`
- `DELETE /disa-returns-backend/test-only/monthly-returns`

Use `GET` to inspect the app clock:

```bash
curl http://localhost:1207/disa-returns-backend/test-only/clock
```

Use `PUT` to set the app date for declaration-period testing. The date must be in `yyyy-MM-dd` format and is applied at `00:00:00Z`:

```bash
curl -X PUT http://localhost:1207/disa-returns-backend/test-only/clock/2026-05-20
```

Use `DELETE` to reset back to the system UTC clock:

```bash
curl -X DELETE http://localhost:1207/disa-returns-backend/test-only/clock
```

For example, set the clock to `2026-05-20` to test declaration attempts outside the configured declaration period.

Use the monthly returns cleanup route to remove all monthly returns from the local database before automation runs:

```bash
curl -X DELETE http://localhost:1207/disa-returns-backend/test-only/monthly-returns
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

### Bruno Collection

The Bruno collection is under `bruno/MonthlyReturn` and is organised into:

- `Create`
- `Declarations`
- `Delete`
- `Get`
- `Update`
- `UpscanCallback`

The `bruno/TestOnly/Clock` folder covers the test-only clock routes. The `bruno/TestOnly/MonthlyReturns` folder clears monthly returns from the local database. These require the service to be running with `-Dapplication.router=testOnlyDoNotUseInAppConf.Routes`; otherwise the routes will not be available.

Monthly return Bruno setup requests call the test-only cleanup route before generating `Zxxxx` references. This avoids collisions with old local data while keeping generated references inside the allowed zReference format. Do not run these Bruno folders in parallel, because cleanup requests delete all monthly returns from the local database.

These Bruno requests clear monthly returns by calling `TestOnly/MonthlyReturns/01-204-clear-monthly-returns` in their pre-request script:

| Request | Why it clears monthly returns |
| --- | --- |
| `MonthlyReturn/Create/01-201-create-nil-return-false` | Creates a clean baseline return and generates `zReference`, `nilZReference`, and `missingZReference`. |
| `MonthlyReturn/Declarations/00-201-create-return-for-declaration` | Creates a clean return to declare and generates declaration references. |
| `MonthlyReturn/Declarations/04-404-declare-missing` | Ensures the declaration reference is missing. |
| `MonthlyReturn/Declarations/06-200-set-clock-outside-period` | Creates a clean outside-period scenario and generates `outsidePeriodZReference`. |
| `MonthlyReturn/Delete/00-201-create-return-for-delete` | Creates a clean return for file delete tests and generates `deleteZReference`. |
| `MonthlyReturn/UpscanCallback/00-201-create-return-for-success-callback` | Creates a clean return for callback tests and generates `callbackZReference`. |

Many later requests run one of these setup requests from their pre-request script, so they can also clear monthly returns indirectly. For example, `MonthlyReturn/Get/01-200-get-existing` runs `MonthlyReturn/Create/01-201-create-nil-return-false`, and `MonthlyReturn/Update/02-200-set-nil-return-false` runs the update setup chain.

Run executable Bruno folders explicitly against a running local service, for example `bru run MonthlyReturn/Get --env Local --bail`. The setup scripts create the data each folder needs. The Upscan callback folder creates a fresh non-nil monthly return with `callbackZReference`, creates a file upload placeholder with `POST /files`, gets that file upload with `GET /files/:reference`, sends a READY callback, then gets the monthly return to confirm the completed file upload was recorded.

Generated Bruno `zReference` values are set as runtime variables with `bru.setVar`, not environment variables, so running Bruno should not rewrite `bruno/environments/Local.bru`.

The callback requests return `202 Accepted`. Completed file upload details appear on the GET after the READY callback in the collection run.

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
