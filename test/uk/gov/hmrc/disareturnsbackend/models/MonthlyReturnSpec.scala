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
import play.api.libs.json.{JsError, JsString, Json}
import uk.gov.hmrc.disareturnsbackend.models.FileUploadFailureReason.*
import uk.gov.hmrc.disareturnsbackend.models.FileUploadStatus.*

import java.time.Instant

class MonthlyReturnSpec extends SpecBase {

  private val zReference         = "Z1234"
  private val taxYear            = "2026"
  private val month              = "5"
  private val uploadReference    = "2b4d6f3a-8c1e-4e4b-9c7a-123456789abc"
  private val downloadUrl        = "https://fus-outbound-bucket.s3.eu-west-2.amazonaws.com/object-key?X-Amz-Signature=abc"
  private val createdOn          = Instant.parse("2026-05-17T12:00:00Z")
  private val completedOn        = Instant.parse("2026-05-17T12:01:00Z")
  private val existingUpdated    = Instant.parse("2026-05-17T11:00:00Z")
  private val fileUploadDetails  = FileUploadDetails(
    fileName = "return.csv",
    fileMimeType = "text/csv",
    uploadTimestamp = Instant.parse("2026-05-17T12:00:00Z"),
    checksum = "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
    size = 1024L
  )
  private val emptyMonthlyReturn = MonthlyReturn(
    zReference = zReference,
    taxYear = taxYear,
    month = month,
    fileUploads = Nil,
    lastUpdated = existingUpdated
  )

  "MonthlyReturn format" - {

    "must round-trip to JSON" in {
      val monthlyReturn = emptyMonthlyReturn.copy(
        fileUploads = List(
          FileUpload(
            reference = uploadReference,
            status = FileUploadStatus.UpscanSuccess,
            createdOn = createdOn,
            completedOn = Some(completedOn),
            fileUploadDetails = Some(fileUploadDetails),
            downloadUrl = Some(downloadUrl)
          )
        ),
        lastUpdated = completedOn
      )

      Json.toJson(monthlyReturn).as[MonthlyReturn] mustBe monthlyReturn
    }
  }

  "createFileUpload" - {

    "must add a CREATED file upload and update lastUpdated" in {
      val result = emptyMonthlyReturn.createFileUpload(uploadReference, createdOn)

      result.fileUploads mustBe List(
        FileUpload(
          reference = uploadReference,
          status = Created,
          createdOn = createdOn
        )
      )
      result.lastUpdated mustBe createdOn
    }

    "must not add a duplicate upload reference" in {
      val existing = emptyMonthlyReturn.createFileUpload(uploadReference, createdOn)

      existing.createFileUpload(uploadReference, completedOn) mustBe existing
    }
  }

  "completeFileUpload" - {

    "must complete a successful file upload" in {
      val monthlyReturn = emptyMonthlyReturn.createFileUpload(uploadReference, createdOn)

      val result = monthlyReturn.completeFileUpload(
        reference = uploadReference,
        status = FileUploadStatus.UpscanSuccess,
        completedOn = completedOn,
        fileUploadDetails = Some(fileUploadDetails),
        downloadUrl = Some(downloadUrl)
      )

      result.fileUploads mustBe List(
        FileUpload(
          reference = uploadReference,
          status = FileUploadStatus.UpscanSuccess,
          createdOn = createdOn,
          completedOn = Some(completedOn),
          fileUploadDetails = Some(fileUploadDetails),
          downloadUrl = Some(downloadUrl)
        )
      )
      result.lastUpdated mustBe completedOn
    }

    "must complete a failed file upload" in {
      val monthlyReturn = emptyMonthlyReturn.createFileUpload(uploadReference, createdOn)

      val result = monthlyReturn.completeFileUpload(
        reference = uploadReference,
        status = UpscanRejected,
        completedOn = completedOn,
        fileUploadDetails = None,
        failureReason = Some(Rejected),
        failureMessage = Some("Duplicate file")
      )

      result.fileUploads mustBe List(
        FileUpload(
          reference = uploadReference,
          status = UpscanRejected,
          createdOn = createdOn,
          completedOn = Some(completedOn),
          failureReason = Some(Rejected),
          failureMessage = Some("Duplicate file")
        )
      )
      result.lastUpdated mustBe completedOn
    }

    "must leave the return unchanged when the reference does not exist" in {
      val monthlyReturn = emptyMonthlyReturn.createFileUpload(uploadReference, createdOn)

      monthlyReturn.completeFileUpload(
        reference = "missing-reference",
        status = FileUploadStatus.UpscanSuccess,
        completedOn = completedOn,
        fileUploadDetails = Some(fileUploadDetails),
        downloadUrl = Some(downloadUrl)
      ) mustBe monthlyReturn
    }
  }

  "FileUploadStatus format" - {

    Seq(
      Created                        -> "CREATED",
      FileUploadStatus.UpscanSuccess -> "UPSCAN_SUCCESS",
      UpscanQuarantine               -> "UPSCAN_QUARANTINE",
      UpscanRejected                 -> "UPSCAN_REJECTED",
      UpscanUnknown                  -> "UPSCAN_UNKNOWN"
    ).foreach { case (modelValue, jsonValue) =>
      s"must serialise and deserialise $jsonValue" in {
        Json.toJson[FileUploadStatus](modelValue) mustBe JsString(jsonValue)
        JsString(jsonValue).as[FileUploadStatus] mustBe modelValue
      }
    }

    "must fail to deserialise an unknown status" in {
      JsString("UNKNOWN_STATUS").validate[FileUploadStatus] mustBe
        JsError("Invalid file upload status: UNKNOWN_STATUS")
    }

    "must fail to deserialise a non-string value" in {
      Json.obj("status" -> "CREATED").validate[FileUploadStatus] mustBe
        JsError("File upload status must be a string")
    }
  }

  "FileUploadFailureReason format" - {

    Seq(
      Quarantine -> "QUARANTINE",
      Rejected   -> "REJECTED",
      Unknown    -> "UNKNOWN"
    ).foreach { case (modelValue, jsonValue) =>
      s"must serialise and deserialise $jsonValue" in {
        Json.toJson[FileUploadFailureReason](modelValue) mustBe JsString(jsonValue)
        JsString(jsonValue).as[FileUploadFailureReason] mustBe modelValue
      }
    }

    "must fail to deserialise an unknown failure reason" in {
      JsString("DUPLICATE").validate[FileUploadFailureReason] mustBe
        JsError("Invalid file upload failure reason: DUPLICATE")
    }

    "must fail to deserialise a non-string value" in {
      Json.obj("failureReason" -> "REJECTED").validate[FileUploadFailureReason] mustBe
        JsError("File upload failure reason must be a string")
    }
  }
}
