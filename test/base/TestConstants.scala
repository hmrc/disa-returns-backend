/*
 * Copyright 2026 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package base

import java.time.Instant

trait TestConstants {

  protected val testServicePath              = "/disa-returns-backend"
  protected val monthlyReturnsCollectionName = "monthlyReturns"

  protected val testZReference          = "Z1234"
  protected val lowercaseTestZReference = "z1234"
  protected val invalidTestZReference   = "1234"

  protected val testTaxYear                = "2026-27"
  protected val yearOnlyTestTaxYear        = "2026"
  protected val invalidTestTaxYear: String = yearOnlyTestTaxYear

  protected val testMonth        = 5
  protected val testRouteMonth   = "5"
  protected val invalidTestMonth = 13

  protected val testUploadReference    = "2b4d6f3a-8c1e-4e4b-9c7a-123456789abc"
  protected val missingUploadReference = "missing-reference"
  protected val testDownloadUrl        =
    "https://fus-outbound-bucket.s3.eu-west-2.amazonaws.com/object-key?X-Amz-Signature=abc"

  protected val testExistingUpdatedOn: Instant   = Instant.parse("2026-05-17T11:00:00Z")
  protected val testRepositoryCreatedOn: Instant = Instant.parse("2026-05-17T11:30:00Z")
  protected val testCreatedOn: Instant           = Instant.parse("2026-05-17T12:00:00Z")
  protected val testUpscanCompletedOn: Instant   = Instant.parse("2026-05-17T12:01:00Z")

  protected val testExistingUpdatedOnString: String      = testExistingUpdatedOn.toString
  protected val testCreatedOnString: String              = testCreatedOn.toString
  protected val testUpscanCompletedOnString: String      = testUpscanCompletedOn.toString
  protected val testExistingUpdatedOnEpochMillis: String = testExistingUpdatedOn.toEpochMilli.toString

  protected val testFileName     = "return.csv"
  protected val testFileMimeType = "text/csv"
  protected val testChecksum     = "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100"
  protected val testFileSize     = 1024L

  protected val zReferenceFieldName        = "zReference"
  protected val taxYearFieldName           = "taxYear"
  protected val monthFieldName             = "month"
  protected val nilReturnFieldName         = "nilReturn"
  protected val fileUploadsFieldName       = "fileUploads"
  protected val createdOnFieldName         = "createdOn"
  protected val upscanCompletedOnFieldName = "upscanCompletedOn"
  protected val lastUpdatedFieldName       = "lastUpdated"
  protected val referenceFieldName         = "reference"
  protected val statusFieldName            = "status"
  protected val valueFieldName             = "value"
  protected val downloadUrlFieldName       = "downloadUrl"
  protected val upscanDownloadUrlFieldName = "upscanDownloadUrl"
  protected val fileUploadDetailsFieldName = "fileUploadDetails"
  protected val fileStatusFieldName        = "fileStatus"
  protected val uploadDetailsFieldName     = "uploadDetails"
  protected val failureDetailsFieldName    = "failureDetails"
  protected val failureReasonFieldName     = "failureReason"
  protected val messageFieldName           = "message"
  protected val fileNameFieldName          = "fileName"
  protected val fileMimeTypeFieldName      = "fileMimeType"
  protected val uploadTimestampFieldName   = "uploadTimestamp"
  protected val checksumFieldName          = "checksum"
  protected val sizeFieldName              = "size"
  protected val mongoDateFieldName         = "$date"
  protected val mongoNumberLongFieldName   = "$numberLong"

  protected val createdStatusString           = "CREATED"
  protected val upscanSuccessStatusString     = "UPSCAN_SUCCESS"
  protected val upscanQuarantineStatusString  = "UPSCAN_QUARANTINE"
  protected val upscanRejectedStatusString    = "UPSCAN_REJECTED"
  protected val upscanUnknownStatusString     = "UPSCAN_UNKNOWN"
  protected val unknownFileUploadStatusString = "UNKNOWN_STATUS"

  protected val readyFileStatusString   = "READY"
  protected val failedFileStatusString  = "FAILED"
  protected val invalidFileStatusString = "SCANNING"

  protected val quarantineReasonString     = "QUARANTINE"
  protected val rejectedReasonString       = "REJECTED"
  protected val unknownReasonString        = "UNKNOWN"
  protected val invalidFailureReasonString = "DUPLICATE"

  protected val testEicarSignatureMessage         = "Eicar-Test-Signature"
  protected val testMimeTypeFailureMessage        =
    "MIME type [application/zip] is not allowed for service: [disa-returns-frontend]"
  protected val testUnableToParseFailureMessage   = "Unable to parse upscan failure details"
  protected val testDuplicateFileMessage          = "Duplicate file"
  protected val testUpscanFailureMessage          = "Upscan failure message"
  protected val testMongoDownMessage              = "mongodb down"
  protected val invalidUpscanResultPayloadMessage = "Invalid UpscanResult payload"
  protected val invalidJsonBody                   = "invalid-json"
}
