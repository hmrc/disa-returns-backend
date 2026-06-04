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

import base.SpecBase
import play.api.http.HeaderNames.CONTENT_TYPE
import play.api.http.MimeTypes.JSON
import play.api.libs.json.{Json, OWrites}
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.disareturnsbackend.controllers.routes.UpscanCallbackController
import uk.gov.hmrc.disareturnsbackend.models.upscan.*

import java.time.Instant

class UpscanCallbackControllerSpec extends SpecBase {

  private val callbackUrl = UpscanCallbackController.callback(
    zReference = "Z1234",
    taxYear = "2026",
    month = "5"
  ).url

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
    downloadUrl = "https://fus-outbound-bucket.s3.eu-west-2.amazonaws.com/object-key?X-Amz-Signature=abc",
    uploadDetails = uploadDetails
  )

  private val failedUploadResult: UpscanUploadResult = UpscanUploadFailure(
    reference = upscanReference,
    failureDetails = UpscanUploadFailureDetails(
      failureReason = UpscanUploadFailureReason.Rejected,
      message = "MIME type [application/zip] is not allowed for service: [disa-returns-frontend]"
    )
  )

  "UpscanCallbackController.callback" - {

    "must return ACCEPTED for valid READY callback payload" in {
      val request =
        FakeRequest(POST, callbackUrl)
          .withJsonBody(Json.toJson(successfulUploadResult))

      val result = route(app, request).value

      status(result) mustBe ACCEPTED
    }

    "must return ACCEPTED for valid FAILED callback payload" in {
      val request =
        FakeRequest(POST, callbackUrl)
          .withJsonBody(Json.toJson(failedUploadResult))

      val result = route(app, request).value

      status(result) mustBe ACCEPTED
    }

    "must return BAD_REQUEST when the payload has an unknown fileStatus" in {
      val request =
        FakeRequest(POST, callbackUrl)
          .withJsonBody(Json.toJson(InvalidFileStatusPayload(upscanReference, "SCANNING")))

      val result = route(app, request).value

      status(result)          mustBe BAD_REQUEST
      contentAsString(result) must include("Invalid UpscanUploadResult payload")
    }

    "must return BAD_REQUEST when the payload has an unknown failureReason" in {
      val request =
        FakeRequest(POST, callbackUrl)
          .withJsonBody(Json.toJson(InvalidFailureReasonPayload(upscanReference, "FAILED", InvalidFailureDetails("DUPLICATE", "Duplicate file"))))

      val result = route(app, request).value

      status(result)          mustBe BAD_REQUEST
      contentAsString(result) must include("Invalid UpscanUploadResult payload")
    }

    "must return BAD_REQUEST when the JSON body is malformed" in {
      val request =
        FakeRequest(POST, callbackUrl)
          .withHeaders(CONTENT_TYPE -> JSON)
          .withTextBody("invalid-json")

      val result = route(app, request).value

      status(result) mustBe BAD_REQUEST
    }

    "must return UNSUPPORTED_MEDIA_TYPE when the request body is invalid JSON and Content-Type unset" in {
      val request =
        FakeRequest(POST, callbackUrl)
          .withTextBody("invalid-json")

      val result = route(app, request).value

      status(result) mustBe UNSUPPORTED_MEDIA_TYPE
    }
  }

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
