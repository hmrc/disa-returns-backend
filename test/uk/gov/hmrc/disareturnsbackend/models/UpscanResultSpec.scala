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

import java.time.Instant

class UpscanResultSpec extends SpecBase {

  private val reference   = "2b4d6f3a-8c1e-4e4b-9c7a-123456789abc"
  private val downloadUrl =
    "https://fus-outbound-bucket.s3.eu-west-2.amazonaws.com/object-key?X-Amz-Signature=abc"

  private val uploadDetails = UpscanDetails(
    fileName = "return.csv",
    fileMimeType = "text/csv",
    uploadTimestamp = Instant.parse("2026-05-17T12:00:00Z"),
    checksum = "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
    size = 1024L
  )

  private val successResult: UpscanResult =
    UpscanSuccess(
      reference = reference,
      downloadUrl = downloadUrl,
      uploadDetails = uploadDetails
    )

  private val successJson =
    Json.parse(
      """
        |{
        |  "reference": "2b4d6f3a-8c1e-4e4b-9c7a-123456789abc",
        |  "downloadUrl": "https://fus-outbound-bucket.s3.eu-west-2.amazonaws.com/object-key?X-Amz-Signature=abc",
        |  "fileStatus": "READY",
        |  "uploadDetails": {
        |    "fileName": "return.csv",
        |    "fileMimeType": "text/csv",
        |    "uploadTimestamp": "2026-05-17T12:00:00Z",
        |    "checksum": "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
        |    "size": 1024
        |  }
        |}
        |""".stripMargin
    )

  "UpscanResult format" - {

    "must serialise a successful upload callback" in {
      Json.toJson(successResult) mustBe successJson
    }

    "must deserialise a successful upload callback" in {
      successJson.as[UpscanResult] mustBe successResult
    }

    "must serialise a failed upload callback" in {
      val result = failedUploadResult(Quarantine, "Eicar-Test-Signature")

      Json.toJson(result) mustBe Json.parse(
        """
          |{
          |  "reference": "2b4d6f3a-8c1e-4e4b-9c7a-123456789abc",
          |  "fileStatus": "FAILED",
          |  "failureDetails": {
          |    "failureReason": "QUARANTINE",
          |    "message": "Eicar-Test-Signature"
          |  }
          |}
          |""".stripMargin
      )
    }

    "must deserialise a failed upload callback for each Upscan failure reason" in
      UpscanFailureReason.values.foreach { failureReason =>
        val result = failedUploadResult(failureReason, "Upscan failure message")

        Json.toJson(result).as[UpscanResult] mustBe result
      }

    "must fail to deserialise an unknown fileStatus" in {
      Json
        .toJson(InvalidFileStatusPayload(reference, "SCANNING"))
        .validate[UpscanResult] mustBe
        JsError("Invalid upscan fileStatus")
    }

    "must fail to deserialise a missing fileStatus" in {
      Json.toJson(MissingFileStatusPayload(reference)).validate[UpscanResult].isError mustBe true
    }

    "must fail to deserialise an invalid failureReason" in {
      Json
        .toJson(
          InvalidFailureReasonPayload(
            reference,
            "FAILED",
            InvalidFailureDetails("DUPLICATE", "Duplicate file")
          )
        )
        .validate[UpscanResult]
        .isError mustBe true
    }

    "must fail to deserialise a successful upload without uploadDetails" in {
      Json
        .toJson(SuccessWithoutUploadDetailsPayload(reference, downloadUrl, "READY"))
        .validate[UpscanResult]
        .isError mustBe true
    }
  }

  private def failedUploadResult(failureReason: UpscanFailureReason, message: String): UpscanResult =
    UpscanFailure(
      reference = reference,
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
