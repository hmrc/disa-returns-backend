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
import play.api.libs.json.{JsError, JsObject, JsString, Json}
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
  private val rowsValidated      = 1
  private val validationErrors   = 0
  private val validation         =
    FileUploadValidationResult(rowsValidated, validationErrors, FileUploadValidationStatus.ValidationSuccess)
  private val inlineErrors       = List(
    FileUploadValidationError(
      rowNumber = 1,
      errorCodes = List(accountNumberRequiredErrorCode, invalidNationalInsuranceErrorCode)
    )
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

  "createFileUpload" - {

    "must add a CREATED file upload and update lastUpdated" in {
      val result = emptyMonthlyReturn.createFileUpload(testUploadReference, testCreatedOn)

      result.fileUploads mustBe List(
        FileUpload(
          reference = testUploadReference,
          status = Created,
          createdOn = testCreatedOn
        )
      )
      result.createdOn mustBe testExistingUpdatedOn
      result.lastUpdated mustBe testCreatedOn
    }

    "must not add a duplicate upload reference" in {
      val existing = emptyMonthlyReturn.createFileUpload(testUploadReference, testCreatedOn)

      existing.createFileUpload(testUploadReference, testUpscanCompletedOn) mustBe existing
    }

    "must not add a file upload to a nil return" in {
      val monthlyReturn = emptyMonthlyReturn.copy(nilReturn = true)

      monthlyReturn.createFileUpload(testUploadReference, testCreatedOn) mustBe monthlyReturn
    }

  }

  "deleteFileUpload" - {

    "must remove a file upload and update lastUpdated" in {
      val monthlyReturn = emptyMonthlyReturn.createFileUpload(testUploadReference, testCreatedOn)

      val result = monthlyReturn.deleteFileUpload(testUploadReference, testUpscanCompletedOn)

      result.fileUploads mustBe Nil
      result.createdOn mustBe testExistingUpdatedOn
      result.lastUpdated mustBe testUpscanCompletedOn
    }

    "must leave the return unchanged when the file upload does not exist" in {
      val monthlyReturn = emptyMonthlyReturn.createFileUpload(testUploadReference, testCreatedOn)

      monthlyReturn.deleteFileUpload(missingUploadReference, testUpscanCompletedOn) mustBe monthlyReturn
    }
  }

  "updateNilReturn" - {

    "must set nilReturn to true and remove all file uploads" in {
      val monthlyReturn = emptyMonthlyReturn.createFileUpload(testUploadReference, testCreatedOn)

      val result = monthlyReturn.updateNilReturn(nilReturn = true, updatedOn = testUpscanCompletedOn)

      result.nilReturn mustBe true
      result.fileUploads mustBe Nil
      result.createdOn mustBe testExistingUpdatedOn
      result.lastUpdated mustBe testUpscanCompletedOn
    }

    "must set nilReturn to false and leave file uploads empty" in {
      val monthlyReturn = emptyMonthlyReturn.copy(nilReturn = true)

      val result = monthlyReturn.updateNilReturn(nilReturn = false, updatedOn = testUpscanCompletedOn)

      result.nilReturn mustBe false
      result.fileUploads mustBe Nil
      result.createdOn mustBe testExistingUpdatedOn
      result.lastUpdated mustBe testUpscanCompletedOn
    }

    "must leave a non-nil return unchanged when setting nilReturn to false" in {
      emptyMonthlyReturn.updateNilReturn(nilReturn = false, updatedOn = testUpscanCompletedOn) mustBe emptyMonthlyReturn
    }
  }

  "completeUpscan" - {

    "must complete a successful file upload" in {
      val monthlyReturn = emptyMonthlyReturn.createFileUpload(testUploadReference, testCreatedOn)

      val result = monthlyReturn.completeUpscan(
        reference = testUploadReference,
        status = FileUploadStatus.UpscanSuccess,
        upscanCompletedOn = testUpscanCompletedOn,
        fileUploadDetails = Some(fileUploadDetails)
      )

      result.fileUploads mustBe List(
        FileUpload(
          reference = testUploadReference,
          status = FileUploadStatus.UpscanSuccess,
          createdOn = testCreatedOn,
          fileUploadDetails = Some(fileUploadDetails.copy(upscanCompletedOn = Some(testUpscanCompletedOn)))
        )
      )
      result.createdOn mustBe testExistingUpdatedOn
      result.lastUpdated mustBe testUpscanCompletedOn
    }

    "must complete a failed file upload" in {
      val monthlyReturn = emptyMonthlyReturn.createFileUpload(testUploadReference, testCreatedOn)

      val result = monthlyReturn.completeUpscan(
        reference = testUploadReference,
        status = UpscanRejected,
        upscanCompletedOn = testUpscanCompletedOn,
        fileUploadDetails = None,
        failureReason = Some(Rejected),
        failureMessage = Some(testDuplicateFileMessage)
      )

      result.fileUploads mustBe List(
        FileUpload(
          reference = testUploadReference,
          status = UpscanRejected,
          createdOn = testCreatedOn,
          failureReason = Some(Rejected),
          failureMessage = Some(testDuplicateFileMessage)
        )
      )
      result.createdOn mustBe testExistingUpdatedOn
      result.lastUpdated mustBe testUpscanCompletedOn
    }

    "must leave other file uploads unchanged when completing a matching upload" in {
      val otherFileUpload = FileUpload("other-reference", FileUploadStatus.Created, testCreatedOn)
      val monthlyReturn   = emptyMonthlyReturn.copy(
        fileUploads = List(
          FileUpload(testUploadReference, FileUploadStatus.Created, testCreatedOn),
          otherFileUpload
        )
      )

      val result = monthlyReturn.completeUpscan(
        reference = testUploadReference,
        status = FileUploadStatus.UpscanSuccess,
        upscanCompletedOn = testUpscanCompletedOn,
        fileUploadDetails = Some(fileUploadDetails)
      )

      result.fileUploads(1) mustBe otherFileUpload
    }

    "must not add a completed file upload when the reference does not exist" in {
      val monthlyReturn = emptyMonthlyReturn.createFileUpload(testUploadReference, testCreatedOn)

      monthlyReturn.completeUpscan(
        reference = missingUploadReference,
        status = FileUploadStatus.UpscanSuccess,
        upscanCompletedOn = testUpscanCompletedOn,
        fileUploadDetails = Some(fileUploadDetails)
      ) mustBe monthlyReturn
    }

    "must leave the return unchanged when completion does not change the upload" in {
      val monthlyReturn = emptyMonthlyReturn.createFileUpload(testUploadReference, testCreatedOn)

      monthlyReturn.completeUpscan(
        reference = testUploadReference,
        status = FileUploadStatus.Created,
        upscanCompletedOn = testUpscanCompletedOn,
        fileUploadDetails = None
      ) mustBe monthlyReturn
    }

    "must complete an existing CREATED file upload" in {
      val monthlyReturn = emptyMonthlyReturn.createFileUpload(testUploadReference, testCreatedOn)

      val result = monthlyReturn.completeUpscan(
        reference = testUploadReference,
        status = FileUploadStatus.UpscanSuccess,
        upscanCompletedOn = testUpscanCompletedOn,
        fileUploadDetails = Some(fileUploadDetails)
      )

      result.fileUploads mustBe List(
        FileUpload(
          reference = testUploadReference,
          status = FileUploadStatus.UpscanSuccess,
          createdOn = testCreatedOn,
          fileUploadDetails = Some(fileUploadDetails.copy(upscanCompletedOn = Some(testUpscanCompletedOn)))
        )
      )
      result.lastUpdated mustBe testUpscanCompletedOn
    }

    "must leave the return unchanged when it is a nil return" in {
      val monthlyReturn = emptyMonthlyReturn
        .createFileUpload(testUploadReference, testCreatedOn)
        .copy(nilReturn = true)

      monthlyReturn.completeUpscan(
        reference = testUploadReference,
        status = FileUploadStatus.UpscanSuccess,
        upscanCompletedOn = testUpscanCompletedOn,
        fileUploadDetails = Some(fileUploadDetails)
      ) mustBe monthlyReturn
    }
  }

  "updateFileUploadProcessingDetails" - {

    "must store object-store locations and validation on the matching upload" in {
      val validationWithInlineErrors = validation.copy(
        status = FileUploadValidationStatus.ValidationFailed,
        validationErrors = 2,
        inlineErrors = inlineErrors
      )
      val monthlyReturn              = emptyMonthlyReturn.copy(
        fileUploads = List(
          FileUpload(testUploadReference, FileUploadStatus.UpscanSuccess, testCreatedOn, Some(fileUploadDetails)),
          FileUpload("other-reference", FileUploadStatus.UpscanSuccess, testCreatedOn, Some(fileUploadDetails))
        )
      )

      val result = monthlyReturn.updateFileUploadProcessingDetails(
        reference = testUploadReference,
        validation = validationWithInlineErrors,
        objectStoreFileLocation = Some("original-location"),
        objectStoreFileErrorsLocation = Some("errors-location"),
        updatedOn = testUpscanCompletedOn
      )

      val updatedDetails = result.fileUploads.head.fileUploadDetails.value
      updatedDetails.objectStoreFileLocation mustBe Some("original-location")
      updatedDetails.objectStoreFileErrorsLocation mustBe Some("errors-location")
      updatedDetails.validation mustBe Some(validationWithInlineErrors)
      result.fileUploads.head.status mustBe FileUploadStatus.ValidationFailure
      result.fileUploads(1) mustBe monthlyReturn.fileUploads(1)
      result.lastUpdated mustBe testUpscanCompletedOn
    }

    "must set validation failure status when validation fails" in {
      val failedValidation = FileUploadValidationResult(1, 1, FileUploadValidationStatus.ValidationFailed)
      val monthlyReturn    = emptyMonthlyReturn.copy(
        fileUploads =
          List(FileUpload(testUploadReference, FileUploadStatus.UpscanSuccess, testCreatedOn, Some(fileUploadDetails)))
      )

      val result = monthlyReturn.updateFileUploadProcessingDetails(
        reference = testUploadReference,
        validation = failedValidation,
        objectStoreFileLocation = Some("original-location"),
        objectStoreFileErrorsLocation = Some("errors-location"),
        updatedOn = testUpscanCompletedOn
      )

      result.fileUploads.head.status mustBe FileUploadStatus.ValidationFailure
    }

    "must set validation failure status when the file is invalid" in {
      val invalidFileValidation = FileUploadValidationResult(0, 0, FileUploadValidationStatus.InvalidFile)
      val monthlyReturn         = emptyMonthlyReturn.copy(
        fileUploads =
          List(FileUpload(testUploadReference, FileUploadStatus.UpscanSuccess, testCreatedOn, Some(fileUploadDetails)))
      )

      val result = monthlyReturn.updateFileUploadProcessingDetails(
        reference = testUploadReference,
        validation = invalidFileValidation,
        objectStoreFileLocation = Some("original-location"),
        objectStoreFileErrorsLocation = None,
        updatedOn = testUpscanCompletedOn
      )

      result.fileUploads.head.status mustBe FileUploadStatus.ValidationFailure
    }

    "must store None for errors location when no error file exists" in {
      val monthlyReturn = emptyMonthlyReturn.copy(
        fileUploads =
          List(FileUpload(testUploadReference, FileUploadStatus.UpscanSuccess, testCreatedOn, Some(fileUploadDetails)))
      )

      val result = monthlyReturn.updateFileUploadProcessingDetails(
        reference = testUploadReference,
        validation = validation,
        objectStoreFileLocation = Some("original-location"),
        objectStoreFileErrorsLocation = None,
        updatedOn = testUpscanCompletedOn
      )

      result.fileUploads.head.fileUploadDetails.value.objectStoreFileErrorsLocation mustBe None
    }

    "must leave the return unchanged when the matching upload has no details" in {
      val monthlyReturn = emptyMonthlyReturn.copy(
        fileUploads = List(FileUpload(testUploadReference, FileUploadStatus.UpscanSuccess, testCreatedOn, None))
      )

      monthlyReturn.updateFileUploadProcessingDetails(
        reference = testUploadReference,
        validation = validation,
        objectStoreFileLocation = Some("original-location"),
        objectStoreFileErrorsLocation = None,
        updatedOn = testUpscanCompletedOn
      ) mustBe monthlyReturn
    }
  }

  "FileUploadStatus format" - {

    Seq(
      Created                            -> createdStatusString,
      FileUploadStatus.UpscanSuccess     -> upscanSuccessStatusString,
      UpscanQuarantine                   -> upscanQuarantineStatusString,
      UpscanRejected                     -> upscanRejectedStatusString,
      UpscanUnknown                      -> upscanUnknownStatusString,
      Duplicate                          -> duplicateStatusString,
      FileUploadStatus.ValidationSuccess -> validationSuccessStatusString,
      FileUploadStatus.ValidationFailure -> validationFailureStatusString
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
