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
import play.api.libs.json.{JsError, Json, OWrites}
import uk.gov.hmrc.disareturnsbackend.models.UpscanFailureReason._

class UpscanResultSpec extends SpecBase {

  private val uploadDetails = UpscanDetails(
    fileName = testFileName,
    fileMimeType = testFileMimeType,
    uploadTimestamp = testCreatedOn,
    checksum = testChecksum,
    size = testFileSize
  )

  private val successResult: UpscanResult =
    UpscanSuccess(
      reference = testUploadReference,
      downloadUrl = testDownloadUrl,
      uploadDetails = uploadDetails
    )

  private val successJson =
    Json.obj(
      referenceFieldName     -> testUploadReference,
      downloadUrlFieldName   -> testDownloadUrl,
      fileStatusFieldName    -> readyFileStatusString,
      uploadDetailsFieldName -> Json.obj(
        fileNameFieldName        -> testFileName,
        fileMimeTypeFieldName    -> testFileMimeType,
        uploadTimestampFieldName -> testCreatedOnString,
        checksumFieldName        -> testChecksum,
        sizeFieldName            -> testFileSize
      )
    )

  "UpscanResult format" - {

    "must serialise a successful upload callback" in {
      Json.toJson(successResult) mustBe successJson
    }

    "must deserialise a successful upload callback" in {
      successJson.as[UpscanResult] mustBe successResult
    }

    "must serialise a failed upload callback" in {
      val result = failedUploadResult(Quarantine, testEicarSignatureMessage)

      Json.toJson(result) mustBe Json.obj(
        referenceFieldName      -> testUploadReference,
        fileStatusFieldName     -> failedFileStatusString,
        failureDetailsFieldName -> Json.obj(
          failureReasonFieldName -> quarantineReasonString,
          messageFieldName       -> testEicarSignatureMessage
        )
      )
    }

    "must deserialise a failed upload callback for each Upscan failure reason" in
      UpscanFailureReason.values.foreach { failureReason =>
        val result = failedUploadResult(failureReason, testUpscanFailureMessage)

        Json.toJson(result).as[UpscanResult] mustBe result
      }

    "must fail to deserialise an unknown fileStatus" in {
      Json
        .toJson(InvalidFileStatusPayload(testUploadReference, invalidFileStatusString))
        .validate[UpscanResult] mustBe
        JsError("Invalid upscan fileStatus")
    }

    "must fail to deserialise a missing fileStatus" in {
      Json.toJson(MissingFileStatusPayload(testUploadReference)).validate[UpscanResult].isError mustBe true
    }

    "must fail to deserialise an invalid failureReason" in {
      Json
        .toJson(
          InvalidFailureReasonPayload(
            testUploadReference,
            failedFileStatusString,
            InvalidFailureDetails(invalidFailureReasonString, testDuplicateFileMessage)
          )
        )
        .validate[UpscanResult]
        .isError mustBe true
    }

    "must fail to deserialise a successful upload without uploadDetails" in {
      Json
        .toJson(SuccessWithoutUploadDetailsPayload(testUploadReference, testDownloadUrl, readyFileStatusString))
        .validate[UpscanResult]
        .isError mustBe true
    }
  }

  private def failedUploadResult(failureReason: UpscanFailureReason, message: String): UpscanResult =
    UpscanFailure(
      reference = testUploadReference,
      failureDetails = UpscanFailureDetails(
        failureReason = failureReason,
        message = message
      )
    )

  private final case class InvalidFileStatusPayload(reference: String, fileStatus: String)

  private object InvalidFileStatusPayload {
    implicit val writes: OWrites[InvalidFileStatusPayload] = Json.writes[InvalidFileStatusPayload]
  }

  private final case class MissingFileStatusPayload(reference: String)

  private object MissingFileStatusPayload {
    implicit val writes: OWrites[MissingFileStatusPayload] = Json.writes[MissingFileStatusPayload]
  }

  private final case class InvalidFailureReasonPayload(
    reference: String,
    fileStatus: String,
    failureDetails: InvalidFailureDetails
  )

  private object InvalidFailureReasonPayload {
    implicit val writes: OWrites[InvalidFailureReasonPayload] = Json.writes[InvalidFailureReasonPayload]
  }

  private final case class InvalidFailureDetails(failureReason: String, message: String)

  private object InvalidFailureDetails {
    implicit val writes: OWrites[InvalidFailureDetails] = Json.writes[InvalidFailureDetails]
  }

  private final case class SuccessWithoutUploadDetailsPayload(
    reference: String,
    downloadUrl: String,
    fileStatus: String
  )

  private object SuccessWithoutUploadDetailsPayload {
    implicit val writes: OWrites[SuccessWithoutUploadDetailsPayload] =
      Json.writes[SuccessWithoutUploadDetailsPayload]
  }
}
