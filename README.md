
# disa-returns-backend

## Endpoints

| Method | Endpoint | Description |
| --- | --- | --- |
| `GET` | `/disa-returns-backend/reporting-window/status/:zReference` | Gets submission's authoritative reporting-window status. |
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

The monthly return and reporting-window status endpoints above require a valid bearer token with an activated
`HMRC-DISA-ORG` enrolment whose `ZREF` identifier matches the path Z-reference.
Requests with no or invalid bearer token return `401 Unauthorized`.
Requests with a valid bearer token but no matching DISA enrolment return `403 Forbidden`.

The Upscan callback and test-only endpoints are not secured by this bearer-token/enrolment check.

### Reporting Window Status

`GET /disa-returns-backend/reporting-window/status/:zReference`

The path Z-reference is normalized to uppercase after authentication and must be in `Z1234` format. The endpoint
proxies `disa-returns-submission`, which remains the source of truth, and returns only:

```json
{
  "reportingWindowOpen": true
}
```

- Returns `400 Bad Request` for an invalid Z-reference.
- Returns `401 Unauthorized` for a missing or invalid bearer token.
- Returns `403 Forbidden` without an activated matching `HMRC-DISA-ORG`/`ZREF` enrolment.
- Returns `503 Service Unavailable` when authentication fails internally or submission's status lookup still fails after its upstream-5xx retries.

### Create Monthly Return

`POST /disa-returns-backend/monthly/:zReference/:taxYear/:month`

Request body:

```json
{
  "nilReturn": false
}
```

- Returns `201 Created` with the generated `submissionId` and the resource path in the `Location` header.
- Returns `409 Conflict` when a monthly return already exists for the same `zReference`, `taxYear`, and `month`.
- Returns `409 Conflict` when `disa-returns-submission` already has a declared monthly return for the same `zReference`, `taxYear`, and `month`.
- Returns `503 Service Unavailable` when MongoDB is unavailable.
- `submissionId` is returned by `disa-returns-submission` at creation time and is stored by the backend for later updates.
- A monthly return created with `"nilReturn": true` is created without file uploads.

Example response:

```json
{
  "submissionId": "1d3df389-98d4-4fd1-b05d-88473fcba6ba"
}
```

### Declare Monthly Return

`POST /disa-returns-backend/monthly/:zReference/:taxYear/:month/declarations`

- Returns `204 No Content` when the record exists and has not already been declared.
- Returns `409 Conflict` when `disa-returns-submission` already has a declared monthly return for the same `zReference`, `taxYear`, and `month`.
- Returns `404 Not Found` when the monthly return does not exist.
- Returns `422 Unprocessable Entity` when the request is outside the declaration period.
- Returns `503 Service Unavailable` when MongoDB is unavailable.
- The declaration window status is read from `disa-returns-submission` using the request Z-reference.
- Before reading the local return, the backend checks submission's reporting-window status; a closed window returns `422`. The status GET retries upstream 5xx failures.
- The declaration POST is not retried. Submission code `MONTHLY_RETURN_ALREADY_DECLARED` maps to backend `409`, and `DECLARATION_PERIOD_CLOSED` maps to backend `422`. An unknown, missing, or malformed submission `422` code is treated as an upstream failure and returns `503`.
- Declarations are recorded in `disa-returns-submission`; this service does not store declaration state locally.

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
- Returns `422 Unprocessable Entity` when `disa-returns-submission` already has a declared monthly return for the period.
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
- The response `declaredOn` value is read from `disa-returns-submission`, which is the source of truth for monthly return declarations.

Example response:

```json
{
  "zReference": "Z1234",
  "submissionId": "1d3df389-98d4-4fd1-b05d-88473fcba6ba",
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
- Returns `422 Unprocessable Entity` when `disa-returns-submission` already has a declared monthly return for the period.
- Returns `503 Service Unavailable` when MongoDB is unavailable.

### Monthly Upscan Callback

- The endpoint accepts Upscan `READY` and `FAILED` callback payloads and returns `202 Accepted` when the payload is valid.
- If the monthly return is a nil return, the callback still returns `202 Accepted` but the file result is not stored.
- If the monthly return is not a nil return, the callback only completes an existing file upload with the same reference in `CREATED` state. Callbacks for missing, deleted, or already-completed references return `202 Accepted` but are not stored.
- Completed file uploads include `fileUploadDetails.upscanCompletedOn`, which records when Upscan processing completed, and `fileUploadDetails.upscanDownloadUrl` for the Upscan download URL.
- If a successful callback has a checksum that already exists on another file upload for the monthly return, the upload is stored with status `DUPLICATE` and is not downloaded or validated.
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

After Upscan success, file upload processing downloads the file, validates it, uploads the original file to object-store as the upload reference, and stores validation metadata under `fileUploadDetails`.
Uploads stored with status `DUPLICATE` skip this processing.
If the Upscan download URL has expired before processing downloads it, the upload is stored with status `UPSCAN_EXPIRED`; the work item is marked as `PermanentlyFailed` and is not retried.

Stored successful validation example:

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
    "upscanDownloadUrl": "https://fus-outbound-bucket.s3.eu-west-2.amazonaws.com/object-key",
    "upscanCompletedOn": "2026-05-17T12:01:00Z",
    "objectStoreFileLocation": "2b4d6f3a-8c1e-4e4b-9c7a-123456789abc",
    "validation": {
      "rowsValidated": 100,
      "validationErrors": 0,
      "status": "ValidationSuccess"
    }
  }
}
```

Stored failed validation example:

```json
{
  "objectStoreFileLocation": "2b4d6f3a-8c1e-4e4b-9c7a-123456789abc",
  "objectStoreFileErrorsLocation": "2b4d6f3a-8c1e-4e4b-9c7a-123456789abc-errors",
  "validation": {
    "rowsValidated": 100,
    "validationErrors": 3,
    "status": "ValidationFailed",
    "inlineErrors": [
      {
        "rowNumber": 1,
        "errorCodes": ["E010", "E022", "E132"]
      }
    ]
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

## Work Items

### File Upload validation

Successful Upscan `READY` callbacks enqueue a file upload work item after the monthly return file upload has been completed successfully, unless the callback is stored as `DUPLICATE`.
The work item contains the monthly return key and upload reference:

```json
{
  "zReference": "Z1234",
  "taxYear": "2026-27",
  "month": 5,
  "reference": "2b4d6f3a-8c1e-4e4b-9c7a-123456789abc"
}
```

The `MonthlyReturnWorkItemJob` polls the `monthlyReturnFileUploadWorkItems` collection and processes outstanding work items asynchronously.

The `MonthlyReturnWorkItemJob` job validates the file upload in `MonthlyReturnFileUploadProcessingService`, and stores the validation result in `FileUploadDetails.validation` field.

The original uploaded file is stored in object-store. If row-level validation errors are found, an errors file is also uploaded to object-store containing the row number and semicolon-separated error codes for each invalid row.

Example errors file:

| RowNumber | ErrorCodes |
| --- | --- |
| `1` | `E010;E022;E132` |
| `2` | `E020;E030` |

The `MonthlyReturnWorkItemJob` is started by `AppInitialiser` after internal-auth token initialisation succeeds. Startup is not blocked while internal-auth initialisation is in progress, but the work item job is not started until it completes successfully.

Work item configuration lives in `conf/application.conf`:

```hocon
contexts {
  monthly-return-file-upload-work-item {
    fork-join-executor {
      parallelism-min = 2
      parallelism-factor = 1.0
      parallelism-max = 8
    }
  }

  file-upload-blocking {
    type = Dispatcher
    executor = "thread-pool-executor"
    throughput = 1

    thread-pool-executor {
      fixed-pool-size = 4
    }
  }
}

monthly-return-file-upload-work-item-job {
  pollInterval = 1 second
  failedRetryAfter = 1 minute
  inProgressRetryAfter = 5 minutes
}

fileUploadMaxInlineErrors = 25
```

Config purpose:

- `contexts.monthly-return-file-upload-work-item` defines the dispatcher used by the background monthly return file upload work item workers.
- `contexts.monthly-return-file-upload-work-item.parallelism-min` sets the minimum number of dispatcher threads available to the job.
- `contexts.monthly-return-file-upload-work-item.parallelism-factor` scales dispatcher threads relative to available processors.
- `contexts.monthly-return-file-upload-work-item.parallelism-max` caps the dispatcher thread pool.
- `contexts.file-upload-blocking` defines the dispatcher used for blocking file-upload work, including CSV/XLSX validation and object-store file size/MD5 calculation.
- `contexts.file-upload-blocking.thread-pool-executor.fixed-pool-size` caps how many blocking file-upload tasks can run at once.
- `monthly-return-file-upload-work-item-job.pollInterval` controls how long the job waits before polling again when no work item is available.
- `monthly-return-file-upload-work-item-job.failedRetryAfter` controls how long a failed work item waits before it can be retried.
- `monthly-return-file-upload-work-item-job.inProgressRetryAfter` controls when an `InProgress` work item becomes eligible to be pulled again if it has not completed. This prevents stuck work items from being hidden forever.
- `fileUploadMaxInlineErrors` controls how many row-level validation error entries are stored inline on `FileUploadValidationResult`. The full errors workbook still contains all row-level validation errors.

### Monthly File Upload Error Codes

Monthly CSV and XLSX uploads return row-level validation error codes in `validation.inlineErrors` and in the uploaded errors workbook. The workbook contains the row number and a semicolon-separated list of these codes.

| Error code | Column name | Description |
| --- | --- | --- |
| `E001` | Row structure | The data row does not contain the expected number of columns. |
| `E010` | `Account Number` | Account number is required. |
| `E011` | `Account Number` | Account number is longer than 20 characters. |
| `E020` | `National Insurance Number` | National Insurance number is required. |
| `E021` | `National Insurance Number` | National Insurance number does not match the expected format. |
| `E022` | `National Insurance Number` | National Insurance number contains invalid characters. |
| `E023` | `National Insurance Number` | National Insurance number is too long. |
| `E024` | `National Insurance Number` | National Insurance number is too short. |
| `E030` | `First Name` | First name is required. |
| `E031` | `First Name` | First name is longer than 50 characters. |
| `E032` | `First Name` | First name contains invalid characters. |
| `E040` | `Middle Name` | Middle name is longer than 50 characters. |
| `E041` | `Middle Name` | Middle name contains invalid characters. |
| `E050` | `Surname` | Surname is required. |
| `E051` | `Surname` | Surname is longer than 50 characters. |
| `E052` | `Surname` | Surname contains invalid characters. |
| `E060` | `Date of Birth` | Date of birth is required. |
| `E061` | `Date of Birth` | Date of birth is not a valid `yyyy-MM-dd` date. |
| `E062` | `Date of Birth` | Date of birth contains invalid characters. |
| `E070` | `ISA Type being reported` | ISA type is required. |
| `E071` | `ISA Type being reported` | ISA type is not one of the supported values. |
| `E080` | `Flexible ISA` | Flexible ISA is required for ISA types where it applies. |
| `E081` | `Flexible ISA` | Flexible ISA must be `Yes` or `No` when provided. |
| `E090` | `Total current year subscriptions transferred in` | Transfer-in amount is required. |
| `E091` | `Total current year subscriptions transferred in` | Transfer-in amount is not a valid money value. |
| `E092` | `Total current year subscriptions transferred in` | Transfer-in amount contains invalid characters. |
| `E100` | `Total current year subscriptions transferred out` | Transfer-out amount is required. |
| `E101` | `Total current year subscriptions transferred out` | Transfer-out amount is not a valid money value. |
| `E102` | `Total current year subscriptions transferred out` | Transfer-out amount contains invalid characters. |
| `E110` | `Date of first subscription event` | First subscription event date is required for LISA rows. |
| `E111` | `Date of first subscription event` | First subscription event date is not a valid `yyyy-MM-dd` date. |
| `E112` | `Date of first subscription event` | First subscription event date contains invalid characters. |
| `E120` | `Date of last subscription event` | Last subscription event date is required. |
| `E121` | `Date of last subscription event` | Last subscription event date is not a valid `yyyy-MM-dd` date. |
| `E122` | `Date of last subscription event` | Last subscription event date is outside the current or previous reporting month for the return period. |
| `E123` | `Date of last subscription event` | Last subscription event date contains invalid characters. |
| `E130` | `Total current year to date subscriptions` | Current-year subscription amount is required. |
| `E131` | `Total current year to date subscriptions` | Current-year subscription amount is not a valid money value. |
| `E132` | `Total current year to date subscriptions` | Current-year subscription amount contains invalid characters. |
| `E140` | `LISA qualifying addition` | LISA qualifying addition is required for LISA rows. |
| `E141` | `LISA qualifying addition` | LISA qualifying addition is not a valid money value. |
| `E142` | `LISA qualifying addition` | LISA qualifying addition contains invalid characters. |
| `E150` | `LISA bonus claim` | LISA bonus claim is required for LISA rows. |
| `E151` | `LISA bonus claim` | LISA bonus claim is not a valid money value. |
| `E152` | `LISA bonus claim` | LISA bonus claim contains invalid characters. |
| `E160` | `Market value of account` | Market value is required. |
| `E161` | `Market value of account` | Market value is not a valid money value. |
| `E162` | `Market value of account` | Market value contains invalid characters. |
| `E170` | `Closure Date` | Closure date is not a valid `yyyy-MM-dd` date. |
| `E171` | `Closure Date` | Closure date is required when a closure reason is provided. |
| `E172` | `Closure Date` | Closure date contains invalid characters. |
| `E180` | `ISA Reason for closure` | ISA closure reason is required when an ISA row has a closure date. |
| `E181` | `ISA Reason for closure` | ISA closure reason is not one of the supported values. |
| `E182` | `ISA Reason for closure` | ISA closure reason contains invalid characters. |
| `E190` | `LISA Reason for closure` | LISA closure reason is required when a LISA row has a closure date. |
| `E191` | `LISA Reason for closure` | LISA closure reason is not one of the supported values. |
| `E192` | `LISA Reason for closure` | LISA closure reason contains invalid characters. |

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

The following test-only routes are available only with that router:

- `GET /disa-returns-backend/test-only/overrides/:zReference`
- `PUT /disa-returns-backend/test-only/overrides/:zReference`
- `DELETE /disa-returns-backend/test-only/overrides/:zReference`
- `DELETE /disa-returns-backend/test-only/monthly-returns`
- `DELETE /disa-returns-backend/test-only/monthly-return-file-upload-work-items`
- `GET /disa-returns-backend/test-only/file-upload/monthly/:filename`
- `GET /disa-returns-backend/test-only/file-upload/monthly-expired-download`

`disa-returns-submission` must also run with the same test-only router before the aggregate override routes can be used.

Monthly-period validation uses the Z-reference-scoped `TimeSource` abstraction. With the normal router, `SystemClock`
returns the current system time in UTC. With the test-only router, `TestOnlySubmissionTimeSource` uses submission's optional
clock override date at midnight UTC, falling back to the backend `SystemClock` when no clock override is present.

Use `GET` to inspect the configured overrides:

```bash
curl http://localhost:1207/disa-returns-backend/test-only/overrides/Z1234
```

Responses contain only `zReference` and nullable `clock` and `reportingWindow` objects. `PUT` fully replaces both
overrides; use `null` to leave either override unset:

```bash
curl -X PUT -H 'Content-Type: application/json' \
  -d '{"clock":{"date":"2026-05-20"},"reportingWindow":null}' \
  http://localhost:1207/disa-returns-backend/test-only/overrides/Z1234
```

Use `DELETE` to clear both overrides:

```bash
curl -X DELETE http://localhost:1207/disa-returns-backend/test-only/overrides/Z1234
```

The backend routes proxy the corresponding Z-reference-scoped aggregate route in `disa-returns-submission` and return
submission's JSON response. Submission is the sole source of truth; backend does not persist overrides. All three
routes return `503 Service Unavailable` when submission cannot satisfy the request.

Use the monthly returns cleanup route to remove all backend monthly returns from the local database before automation runs:

```bash
curl -X DELETE http://localhost:1207/disa-returns-backend/test-only/monthly-returns
```

Use the monthly return file-upload work-item cleanup route to delete all monthly return file-upload work-item documents from `monthlyReturnFileUploadWorkItems`:

```bash
curl -X DELETE http://localhost:1207/disa-returns-backend/test-only/monthly-return-file-upload-work-items
```

This deletes all local file-upload validation work-item documents, regardless of status; it does not drop the collection.

### Running the test suite

To run the unit tests:

```bash
sbt test
```

To run the integration tests:

```bash
sbt it/test
```

### Test-Only Endpoints

Test-only endpoints require the service to be running with `-Dapplication.router=testOnlyDoNotUseInAppConf.Routes`.
Otherwise the routes will not be available.

| Endpoint | Used by | Purpose |
| --- | --- | --- |
| `GET /disa-returns-backend/test-only/overrides/:zReference` | Frontend test utility and `bruno/TestOnly/Overrides` | Read submission's aggregate overrides. |
| `PUT /disa-returns-backend/test-only/overrides/:zReference` | Frontend test utility and `bruno/TestOnly/Overrides` | Fully replace submission's clock and reporting-window overrides. |
| `DELETE /disa-returns-backend/test-only/overrides/:zReference` | Frontend test utility and `bruno/TestOnly/Overrides` | Clear both submission overrides. |
| `DELETE /disa-returns-backend/test-only/monthly-returns` | `bruno/TestOnly/MonthlyReturns` | Remove all backend monthly returns from the local database before automation runs. |
| `DELETE /disa-returns-backend/test-only/monthly-return-file-upload-work-items` | `bruno/TestOnly/MonthlyReturnFileUploadWorkItems` | Delete all local file-upload validation work-item documents from `monthlyReturnFileUploadWorkItems`. |
| `GET /disa-returns-backend/test-only/file-upload/monthly/:filename` | `bruno/MonthlyReturn/Validation` | Serve copied local validation tests from `conf/test-only/file-upload/monthly` so Bruno can use them as Upscan `downloadUrl` values. |
| `GET /disa-returns-backend/test-only/file-upload/monthly-expired-download` | `bruno/MonthlyReturn/Validation/21-200-upscan-expired` | Return an S3-style expired Upscan download response so Bruno can verify `UPSCAN_EXPIRED` processing. |

`bruno/TestOnly/MonthlyReturns/01-204-clear-monthly-returns` also clears monthly returns for the collection's configured Z-references from `disa-returns-submission` before calling the backend cleanup route, so Bruno scenarios start from a clean declaration source of truth.

The monthly file-upload test endpoint only serves simple `.csv` and `.xlsx` filenames copied into `conf/test-only/file-upload/monthly`. The files are runtime resources so they are available when running locally or through service-manager with the test-only router enabled. The expired-download endpoint does not serve a file; it returns `403 Forbidden` with an S3 `AccessDenied` / `Request has expired` XML body.

#### Override test utilities

The aggregate override routes proxy state maintained by `disa-returns-submission`; backend does not persist override
state locally. `PUT` is a full replacement, not a patch. `DELETE` clears both the clock and reporting-window override.

```json
{
  "clock": { "date": "2026-06-20" },
  "reportingWindow": {
    "startDate": "2026-06-19T23:59:00Z",
    "endDate": "2026-06-20T00:01:00Z"
  }
}
```

Each route returns aggregate JSON containing only `zReference` and nullable `clock` and `reportingWindow` objects.
Invalid Z-references or malformed replacement bodies return `400`; submission failures return `503`.

Monthly file-upload validation tests are maintained in `it/resources/file-upload/monthly` for integration tests and copied to `conf/test-only/file-upload/monthly` for local Bruno/service-manager use. The same filenames exist in both locations.

#### Monthly File Upload Test Files

| Name | Formats | What it tests |
| --- | --- | --- |
| `blank-invalid` | `.csv`, `.xlsx` | Blank or effectively empty file. Expected result: `VALIDATION_FAILURE` with validation status `InvalidFile` and no errors workbook. |
| `empty-row-invalid` | `.csv`, `.xlsx` | Header plus an empty data row. Expected result: `VALIDATION_FAILURE` with required-field inline errors and an errors workbook. |
| `garbage-invalid` | `.csv`, `.xlsx` | File content cannot be parsed as a valid upload in the declared format. Expected result: `VALIDATION_FAILURE` with validation status `InvalidFile` and no errors workbook. |
| `inline-error-limit-exceeded-invalid` | `.csv`, `.xlsx` | Multiple invalid rows to prove inline errors are capped while the full errors workbook is still uploaded. Expected result: `VALIDATION_FAILURE` with an errors workbook. |
| `invalid-header` | `.csv`, `.xlsx` | Header row does not match the expected monthly upload template. Expected result: `VALIDATION_FAILURE` with validation status `InvalidFile` and no errors workbook. |
| `isa-closed-valid` | `.csv`, `.xlsx` | Valid closed ISA monthly return row with closure fields. Expected result: `VALIDATION_SUCCESS`. |
| `isa-open-valid` | `.csv`, `.xlsx` | Valid open ISA monthly return row. Expected result: `VALIDATION_SUCCESS`. |
| `lisa-closed-valid` | `.csv`, `.xlsx` | Valid closed LISA monthly return row with closure fields. Expected result: `VALIDATION_SUCCESS`. |
| `lisa-open-valid` | `.csv`, `.xlsx` | Valid open LISA monthly return row with LISA-specific fields. Expected result: `VALIDATION_SUCCESS`. |
| `row-errors-invalid` | `.csv`, `.xlsx` | One data row with row-level validation errors for national insurance number, first name, and current year subscriptions. Expected result: `VALIDATION_FAILURE` with an errors workbook. |

When using `OBJECT_STORE_STUB` locally, object-store stores files under its filesystem-backed stub and exposes them through its list endpoint. After a successful validation run, this command lists files uploaded by `disa-returns-backend`:

```bash
curl -s \
  -H "Authorization: valid-internal-auth-token-disa-returns-backend" \
  http://localhost:8464/object-store/list/disa-returns-backend | jq
```

The `Authorization` header must match `internal-auth.token`. The path `disa-returns-backend` must match the object-store resource location used by this service. A successful response includes uploaded original files, and row-level validation failures also upload an `-errors` workbook.

To download one of the listed files, call the `location` returned by the list response on the same object-store host. For example:

```bash
curl -s \
  -H "Authorization: valid-internal-auth-token-disa-returns-backend" \
  http://localhost:8464/object-store/object/disa-returns-backend/validation-isa-open-valid-csv \
  --output validation-isa-open-valid.csv
```

For an errors workbook, use the `-errors` location and save it as `.xlsx`:

```bash
curl -s \
  -H "Authorization: valid-internal-auth-token-disa-returns-backend" \
  http://localhost:8464/object-store/object/disa-returns-backend/validation-row-errors-invalid-csv-errors \
  --output validation-row-errors-invalid-csv-errors.xlsx
```

### Bruno Collection

The Bruno collection is under `bruno/MonthlyReturn` and is organised into:

- `Create`
- `Declarations`
- `Delete`
- `Get`
- `Update`
- `UpscanCallback`
- `Validation`

Monthly return Bruno setup requests call the test-only cleanup route for the collection's configured Z-references. This avoids collisions with old backend and `disa-returns-submission` data. Upscan callback setup also clears monthly return file-upload work items before creating a fresh callback scenario. Do not run these Bruno folders in parallel, because cleanup requests delete all monthly returns from the backend, delete scoped monthly returns from `disa-returns-submission`, and delete monthly return file-upload work items from the local database.

These Bruno requests clear monthly returns by calling `TestOnly/MonthlyReturns/01-204-clear-monthly-returns` in their pre-request script:

| Request | Why it clears monthly returns |
| --- | --- |
| `MonthlyReturn/Create/01-201-create-nil-return-false` | Creates a clean baseline return using the configured `zReference`, `nilZReference`, and `missingZReference`. |
| `MonthlyReturn/Create/05-409-create-after-submission-declaration` | Creates and declares a return directly in `disa-returns-submission`, then verifies backend create is blocked. |
| `MonthlyReturn/Declarations/00-201-create-return-for-declaration` | Creates a clean return using the configured declaration references. |
| `MonthlyReturn/Declarations/04-404-declare-missing` | Ensures the declaration reference is missing. |
| `MonthlyReturn/Declarations/07-201-create-return-outside-period` | Clears existing returns, then creates the clean return used for the outside-period declaration scenario; request `06` only applies the outside-period clock override. |
| `MonthlyReturn/Declarations/11-409-declare-after-submission-declaration` | Creates a backend return, declares it directly in `disa-returns-submission`, then verifies backend declaration is blocked. |
| `MonthlyReturn/Declarations/12-422-create-file-after-submission-declaration` | Creates a backend return, declares it directly in `disa-returns-submission`, then verifies file creation is blocked. |
| `MonthlyReturn/Delete/00-201-create-return-for-delete` | Creates a clean return for file delete tests using the configured `deleteZReference`. |
| `MonthlyReturn/Update/07-422-update-after-submission-declaration` | Creates a backend return, declares it directly in `disa-returns-submission`, then verifies nil-return updates are blocked. |
| `MonthlyReturn/UpscanCallback/00-201-create-return-for-success-callback` | Clears monthly return file-upload work items and creates a clean callback scenario using the configured `callbackZReference`. |

Many later requests run one of these setup requests from their pre-request script, so they can also clear monthly returns indirectly. For example, `MonthlyReturn/Get/01-200-get-existing` runs `MonthlyReturn/Create/01-201-create-nil-return-false`, and `MonthlyReturn/Update/02-200-set-nil-return-false` runs the update setup chain.

Run executable Bruno folders explicitly against a running local service, for example `bru run MonthlyReturn/Get --env Local --bail`. The setup scripts create the data each folder needs. The Upscan callback folder creates a fresh non-nil monthly return with `callbackZReference`, creates a file upload placeholder with `POST /files`, gets that file upload with `GET /files/:reference`, sends a READY callback, then gets the monthly return to confirm the completed file upload was recorded. It also includes a duplicate callback flow that creates a second upload with the same checksum and asserts the stored status is `DUPLICATE`.

The Validation folder uses the copied monthly file-upload tests as local Upscan downloads. Each validation request creates a fresh file-upload scenario, sends a READY callback with a test-only file URL, waits for processing to reach the expected upload status, then asserts the final validation result and object-store locations. Scenario `21-200-upscan-expired` uses the expired-download test endpoint and asserts the terminal `UPSCAN_EXPIRED` status.

`bruno/TestOnly/ValidationHelpers` contains support requests used by the validation scenarios. They are guarded with the runtime variable `runValidationHelpers`, so they no-op safely if the whole collection runs them directly.

`bruno/TestOnly/Overrides` verifies aggregate GET, replacement, deletion, combined clock/reporting-window payloads,
and invalid Z-reference handling. Shared clock setup and cleanup requests operate on the configured monthly-return
Z-references; the combined-field request uses `overrideZReference`, `reportingWindowOverrideStart`, and
`reportingWindowOverrideEnd`.

The backend override Bruno helpers call backend only; backend proxies each scoped operation to submission. They do not
test the production reporting-window status route.

`validationProcessingPollIntervalMs` is defined in `bruno/environments/Local.bru` and defaults to `1000`. It is used by the processed validation requests in `bruno/MonthlyReturn/Validation`, currently the test-specific `01` to `21` checks. The polling gives the asynchronous monthly return file-upload work-item job time to download, validate, upload to object-store, and update Mongo before Bruno performs the final GET/assertions.

To change it permanently for local runs, edit `bruno/environments/Local.bru`:

```text
validationProcessingPollIntervalMs: 1500
```

Bruno Z-references are configured in `bruno/environments/Local.bru` and copied into runtime variables with
`bru.setVar`; requests do not generate random references or rewrite the environment file.

The collection pre-request script logs in through `auth-login-api` once per run and creates an activated
`HMRC-DISA-ORG`/`ZREF` enrolment for every configured monthly-return Z-reference. It supplies the returned bearer token
to secured backend requests.

The callback requests return `202 Accepted`. Completed file upload details appear on the GET after the READY callback in the collection run. Duplicate callback details appear on the GET after the duplicate callback with status `DUPLICATE`.

### Before you commit

This service leverages scalaFmt to ensure that the code is formatted correctly.

Before you commit, please run the following commands to check that the code is formatted correctly:

```bash
# formats all source files, runs unit and integration tests, and produces a coverage report
sbt precommit

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

This code is open source software licensed under the [Apache 2.0 License](https://www.apache.org/licenses/LICENSE-2.0.html).
