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

package uk.gov.hmrc.disareturnsbackend.controllers

import play.api.http.MimeTypes
import play.api.http.MimeTypes.JSON
import play.api.http.Status.{ACCEPTED, BAD_REQUEST, NOT_FOUND, UNSUPPORTED_MEDIA_TYPE}
import play.api.libs.json.{Json, OWrites}
import play.api.libs.ws.readableAsString
import uk.gov.hmrc.disareturnsbackend.BaseIntegrationSpec
import uk.gov.hmrc.disareturnsbackend.models.upscan.{
  UpscanUploadDetails,
  UpscanUploadFailure,
  UpscanUploadFailureDetails,
  UpscanUploadFailureReason,
  UpscanUploadResult,
  UpscanUploadSuccess
}

import java.time.Instant

class UpscanCallbackControllerISpec extends BaseIntegrationSpec {

  private val servicePath =
    "/disa-returns-backend"

  private val callbackPath = "/monthly/upscan/callback/Z1234/2026/5"

  private val fullCallbackPath = servicePath + callbackPath

  private val downloadUrl =
    "https://fus-outbound-bucket.s3.eu-west-2.amazonaws.com/object-key?X-Amz-Signature=abc"

  private val upscanReference = "2b4d6f3a-8c1e-4e4b-9c7a-123456789abc"

  private val uploadDetails = UpscanUploadDetails(
    fileName = "return.csv",
    fileMimeType = "text/csv",
    uploadTimestamp = Instant.parse("2026-05-17T12:00:00Z"),
    checksum = "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
    size = 1024L
  )

  private val successfulUploadResult: UpscanUploadResult = UpscanUploadSuccess(
    reference = upscanReference,
    downloadUrl = downloadUrl,
    uploadDetails = uploadDetails
  )

  "POST to upscan callback" should {

    "return 202 Accepted for READY callback payload" in {
      val result = postUploadResult(successfulUploadResult)

      result.status shouldBe ACCEPTED
    }

    "return 202 Accepted for FAILED QUARANTINE callback payload" in {
      val result = postUploadResult(
        failedUploadResult(UpscanUploadFailureReason.Quarantine, "Eicar-Test-Signature")
      )

      result.status shouldBe ACCEPTED
    }

    "return 202 Accepted for FAILED REJECTED callback payload" in {
      val result = postUploadResult(
        failedUploadResult(
          UpscanUploadFailureReason.Rejected,
          "MIME type [application/zip] is not allowed for service: [disa-returns-frontend]"
        )
      )

      result.status shouldBe ACCEPTED
    }

    "return 202 Accepted for FAILED UNKNOWN callback payload" in {
      val result = postUploadResult(
        failedUploadResult(UpscanUploadFailureReason.Unknown, "Unable to parse upscan failure details")
      )

      result.status shouldBe ACCEPTED
    }

    "return 400 Bad Request when fileStatus is invalid" in {
      val result = postJson(
        fullCallbackPath,
        Json.toJson(InvalidFileStatusPayload(upscanReference, "SCANNING"))
      )

      result.status shouldBe BAD_REQUEST
      result.body   should include("Invalid UpscanUploadResult payload")
    }

    "return 400 Bad Request when failureReason is invalid" in {
      val result = postJson(
        fullCallbackPath,
        Json.toJson(
          InvalidFailureReasonPayload(
            upscanReference,
            "FAILED",
            InvalidFailureDetails("DUPLICATE", "Duplicate file")
          )
        )
      )

      result.status shouldBe BAD_REQUEST
      result.body   should include("Invalid UpscanUploadResult payload")
    }

    "return 400 Bad Request when the JSON body is malformed" in {
      val result = postString(fullCallbackPath, "invalid-json", JSON)

      result.status shouldBe BAD_REQUEST
    }

    "return 415 Unsupported Media Type when the request body is not JSON" in {
      val result = postString(fullCallbackPath, "invalid-json", MimeTypes.TEXT)

      result.status shouldBe UNSUPPORTED_MEDIA_TYPE
    }

    "return 404 Not Found when the service prefix is missing" in {
      val result = postJson(callbackPath, Json.toJson(successfulUploadResult))

      result.status shouldBe NOT_FOUND
    }
  }

  private def failedUploadResult(failureReason: UpscanUploadFailureReason, message: String): UpscanUploadResult =
    UpscanUploadFailure(
      reference = upscanReference,
      failureDetails = UpscanUploadFailureDetails(
        failureReason = failureReason,
        message = message
      )
    )

  private def postUploadResult(body: UpscanUploadResult) =
    postJson(fullCallbackPath, Json.toJson(body))

  private final case class InvalidFileStatusPayload(reference: String, fileStatus: String)

  private object InvalidFileStatusPayload {
    implicit val writes: OWrites[InvalidFileStatusPayload] = Json.writes[InvalidFileStatusPayload]
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
}
