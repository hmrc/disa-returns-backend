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

package uk.gov.hmrc.disareturnsbackend.models

import base.SpecBase
import play.api.libs.json.*
import uk.gov.hmrc.disareturnsbackend.models.FileUploadFailureReason.*
import uk.gov.hmrc.disareturnsbackend.models.FileUploadStatus.*

class MonthlyReturnSpec extends SpecBase {

  private val fileUploadDetails  = FileUploadDetails(
    fileName = testFileName,
    fileMimeType = testFileMimeType,
    uploadTimestamp = testCreatedOn,
    checksum = testChecksum,
    size = testFileSize,
    upscanDownloadUrl = testDownloadUrl
  )
  private val emptyMonthlyReturn = MonthlyReturn(
    zReference = testZReference,
    submissionId = testSubmissionId,
    taxYear = yearOnlyTestTaxYear,
    month = testMonth,
    createdOn = testExistingUpdatedOn,
    fileUploads = Nil,
    lastUpdated = testExistingUpdatedOn
  )

  "fileUploadsReadyForSubmission" - {
    "must include only validated uploads with an object-store location" in {
      val ready      = FileUpload(
        testUploadReference,
        FileUploadStatus.ValidationSuccess,
        testCreatedOn,
        Some(fileUploadDetails.copy(objectStoreFileLocation = Some("object-store-location")))
      )
      val noLocation = FileUpload(
        "no-location",
        FileUploadStatus.ValidationSuccess,
        testCreatedOn,
        Some(fileUploadDetails)
      )
      val failed     = ready.copy(reference = "failed", status = FileUploadStatus.ValidationFailure)

      emptyMonthlyReturn.copy(fileUploads = List(ready, noLocation, failed)).fileUploadsReadyForSubmission mustBe List(
        ready
      )
    }
  }

  "MonthlyReturn format" - {

    "must round-trip to JSON" in {
      val monthlyReturn = emptyMonthlyReturn.copy(
        nilReturn = true,
        fileUploads = List(
          FileUpload(
            reference = testUploadReference,
            status = FileUploadStatus.UpscanSuccess,
            createdOn = testCreatedOn,
            fileUploadDetails = Some(fileUploadDetails.copy(upscanCompletedOn = Some(testUpscanCompletedOn)))
          )
        ),
        lastUpdated = testUpscanCompletedOn
      )

      Json.toJson(monthlyReturn).as[MonthlyReturn] mustBe monthlyReturn
    }

    "must write upscan completed instants inside fileUploadDetails as upscanCompletedOn" in {
      val monthlyReturn = emptyMonthlyReturn.copy(
        fileUploads = List(
          FileUpload(
            reference = testUploadReference,
            status = FileUploadStatus.UpscanSuccess,
            createdOn = testCreatedOn,
            fileUploadDetails = Some(fileUploadDetails.copy(upscanCompletedOn = Some(testUpscanCompletedOn)))
          )
        )
      )

      val json = Json.toJson(monthlyReturn)

      (((json \ fileUploadsFieldName)(0) \ fileUploadDetailsFieldName) \ upscanCompletedOnFieldName).as[JsString] mustBe
        JsString(testUpscanCompletedOnString)
      (((json \ fileUploadsFieldName)(0) \ fileUploadDetailsFieldName) \ upscanDownloadUrlFieldName).as[JsString] mustBe
        JsString(testDownloadUrl)
    }

    "must write instants as ISO strings for API JSON" in {
      val json = Json.toJson(emptyMonthlyReturn)

      (json \ createdOnFieldName).as[JsString] mustBe JsString(testExistingUpdatedOnString)
      (json \ lastUpdatedFieldName).as[JsString] mustBe JsString(testExistingUpdatedOnString)
    }

    "must write instants as Mongo date objects for Mongo JSON" in {
      val json = Json.toJson(emptyMonthlyReturn)(MonthlyReturn.mongoFormat)

      ((json \ createdOnFieldName) \ mongoDateFieldName \ mongoNumberLongFieldName).as[JsString] mustBe
        JsString(testExistingUpdatedOnEpochMillis)
      ((json \ lastUpdatedFieldName) \ mongoDateFieldName \ mongoNumberLongFieldName).as[JsString] mustBe
        JsString(testExistingUpdatedOnEpochMillis)
    }

    "must default nilReturn to false when reading existing JSON" in {
      val jsonWithoutNilReturn = Json.toJson(emptyMonthlyReturn).as[JsObject] - nilReturnFieldName

      jsonWithoutNilReturn.as[MonthlyReturn] mustBe emptyMonthlyReturn
    }

    "must default createdOn to lastUpdated when reading existing JSON" in {
      val jsonWithoutCreatedOn = Json.toJson(emptyMonthlyReturn).as[JsObject] - createdOnFieldName

      jsonWithoutCreatedOn.as[MonthlyReturn] mustBe emptyMonthlyReturn
    }

    "must default createdOn to lastUpdated when reading existing Mongo JSON" in {
      val jsonWithoutCreatedOn =
        Json.toJson(emptyMonthlyReturn)(MonthlyReturn.mongoFormat).as[JsObject] - createdOnFieldName

      jsonWithoutCreatedOn.as[MonthlyReturn](MonthlyReturn.mongoFormat) mustBe emptyMonthlyReturn
    }
  }

  "canCompleteUpscan" - {

    "must accept a matching CREATED upload on a non-nil return" in {
      val monthlyReturn = emptyMonthlyReturn.copy(
        fileUploads = List(FileUpload(testUploadReference, Created, testCreatedOn))
      )

      monthlyReturn.canCompleteUpscan(testUploadReference) mustBe true
    }

    "must reject a missing upload reference" in {
      val monthlyReturn = emptyMonthlyReturn.copy(
        fileUploads = List(FileUpload(testUploadReference, Created, testCreatedOn))
      )

      monthlyReturn.canCompleteUpscan(missingUploadReference) mustBe false
      emptyMonthlyReturn.canCompleteUpscan(testUploadReference) mustBe false
    }

    "must reject uploads that have already left CREATED status" in
      FileUploadStatus.values.filterNot(_ == Created).foreach { status =>
        val monthlyReturn = emptyMonthlyReturn.copy(
          fileUploads = List(FileUpload(testUploadReference, status, testCreatedOn))
        )

        withClue(s"status [${status.value}]") {
          monthlyReturn.canCompleteUpscan(testUploadReference) mustBe false
        }
      }

    "must reject a nil return even if it contains a matching CREATED upload" in {
      val monthlyReturn = emptyMonthlyReturn.copy(
        nilReturn = true,
        fileUploads = List(FileUpload(testUploadReference, Created, testCreatedOn))
      )

      monthlyReturn.canCompleteUpscan(testUploadReference) mustBe false
    }
  }

  "hasSuccessfulFileUploadMatchingChecksumAndNotMatchingReference" - {

    val successfulStatuses = Seq(FileUploadStatus.ValidationSuccess, FileUploadStatus.Submitted)

    "must identify another successful upload with the same checksum" in
      successfulStatuses.foreach { status =>
        val monthlyReturn = emptyMonthlyReturn.copy(
          fileUploads = List(
            FileUpload("existing-reference", status, testCreatedOn, fileUploadDetails = Some(fileUploadDetails))
          )
        )

        withClue(s"status [${status.value}]") {
          monthlyReturn.hasSuccessfulFileUploadMatchingChecksumAndNotMatchingReference(
            testUploadReference,
            testChecksum
          ) mustBe true
        }
      }

    "must ignore uploads that have not completed successfully" in
      FileUploadStatus.values.filterNot(successfulStatuses.contains).foreach { status =>
        val monthlyReturn = emptyMonthlyReturn.copy(
          fileUploads = List(
            FileUpload("existing-reference", status, testCreatedOn, fileUploadDetails = Some(fileUploadDetails))
          )
        )

        withClue(s"status [${status.value}]") {
          monthlyReturn.hasSuccessfulFileUploadMatchingChecksumAndNotMatchingReference(
            testUploadReference,
            testChecksum
          ) mustBe false
        }
      }

    "must ignore the upload being checked" in {
      val monthlyReturn = emptyMonthlyReturn.copy(
        fileUploads = List(
          FileUpload(
            testUploadReference,
            FileUploadStatus.ValidationSuccess,
            testCreatedOn,
            fileUploadDetails = Some(fileUploadDetails)
          )
        )
      )

      monthlyReturn.hasSuccessfulFileUploadMatchingChecksumAndNotMatchingReference(
        testUploadReference,
        testChecksum
      ) mustBe false
    }
  }

  "FileUploadStatus format" - {

    Seq(
      Created                            -> createdStatusString,
      FileUploadStatus.UpscanSuccess     -> upscanSuccessStatusString,
      UpscanQuarantine                   -> upscanQuarantineStatusString,
      UpscanRejected                     -> upscanRejectedStatusString,
      UpscanUnknown                      -> upscanUnknownStatusString,
      UpscanExpired                      -> upscanExpiredStatusString,
      Duplicate                          -> duplicateStatusString,
      FileUploadStatus.ValidationSuccess -> validationSuccessStatusString,
      FileUploadStatus.ValidationFailure -> validationFailureStatusString,
      FileUploadStatus.Submitted         -> "SUBMITTED"
    ).foreach { case (modelValue, jsonValue) =>
      s"must serialise and deserialise $jsonValue" in {
        Json.toJson[FileUploadStatus](modelValue) mustBe JsString(jsonValue)
        JsString(jsonValue).as[FileUploadStatus] mustBe modelValue
      }
    }

    "must fail to deserialise an unknown status" in {
      JsString(unknownFileUploadStatusString).validate[FileUploadStatus] mustBe
        JsError(s"Invalid file upload status: $unknownFileUploadStatusString")
    }

    "must fail to deserialise a non-string value" in {
      Json.obj(statusFieldName -> createdStatusString).validate[FileUploadStatus] mustBe
        JsError("File upload status must be a string")
    }
  }

  "FileUploadFailureReason format" - {

    Seq(
      Quarantine -> quarantineReasonString,
      Rejected   -> rejectedReasonString,
      Unknown    -> unknownReasonString
    ).foreach { case (modelValue, jsonValue) =>
      s"must serialise and deserialise $jsonValue" in {
        Json.toJson[FileUploadFailureReason](modelValue) mustBe JsString(jsonValue)
        JsString(jsonValue).as[FileUploadFailureReason] mustBe modelValue
      }
    }

    "must fail to deserialise an unknown failure reason" in {
      JsString(invalidFailureReasonString).validate[FileUploadFailureReason] mustBe
        JsError(s"Invalid file upload failure reason: $invalidFailureReasonString")
    }

    "must fail to deserialise a non-string value" in {
      Json.obj(failureReasonFieldName -> rejectedReasonString).validate[FileUploadFailureReason] mustBe
        JsError("File upload failure reason must be a string")
    }
  }
}
