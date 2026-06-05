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
import org.mockito.ArgumentMatchers.eq as eqTo
import org.mockito.Mockito.{reset, verify, when}
import org.scalatest.BeforeAndAfterEach
import play.api.Application
import play.api.inject.bind
import play.api.libs.json.{JsValue, Json, OWrites}
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.disareturnsbackend.models.*
import uk.gov.hmrc.disareturnsbackend.services.UpscanCallbackService

import java.time.Instant
import scala.concurrent.Future

class UpscanCallbackControllerSpec extends SpecBase with BeforeAndAfterEach {

  private val mockUpscanCallbackService = mock[UpscanCallbackService]

  override lazy val app: Application = applicationBuilder(
    Seq(
      bind[UpscanCallbackService].toInstance(mockUpscanCallbackService)
    )
  ).configure("play.http.router" -> "prod.Routes")
    .build()

  private lazy val controller = app.injector.instanceOf[UpscanCallbackController]

  private val upscanReference = "2b4d6f3a-8c1e-4e4b-9c7a-123456789abc"

  private val uploadDetails = UpscanDetails(
    fileName = "return.csv",
    fileMimeType = "text/csv",
    uploadTimestamp = Instant.parse("2026-05-17T12:00:00Z"),
    checksum = "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
    size = 1024L
  )

  private val successfulUploadResult: UpscanResult = UpscanSuccess(
    reference = upscanReference,
    downloadUrl = "https://fus-outbound-bucket.s3.eu-west-2.amazonaws.com/object-key?X-Amz-Signature=abc",
    uploadDetails = uploadDetails
  )

  private val failedUploadResult: UpscanResult = UpscanFailure(
    reference = upscanReference,
    failureDetails = UpscanFailureDetails(
      failureReason = UpscanFailureReason.Rejected,
      message = "MIME type [application/zip] is not allowed for service: [disa-returns-frontend]"
    )
  )

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockUpscanCallbackService)
  }

  "UpscanCallbackController.monthlyReturnUpscanCallback" - {

    "must return ACCEPTED for valid READY callback payload" in {
      when(
        mockUpscanCallbackService.monthlyReturnUpscanCallback(
          eqTo("Z1234"),
          eqTo("2026"),
          eqTo("5"),
          eqTo(successfulUploadResult)
        )
      ).thenReturn(Future.successful(()))

      val result = monthlyReturnUpscanCallback(Json.toJson(successfulUploadResult))

      status(result) mustBe ACCEPTED
      verify(mockUpscanCallbackService).monthlyReturnUpscanCallback(
        eqTo("Z1234"),
        eqTo("2026"),
        eqTo("5"),
        eqTo(successfulUploadResult)
      )
    }

    "must return ACCEPTED for valid FAILED callback payload" in {
      when(
        mockUpscanCallbackService.monthlyReturnUpscanCallback(
          eqTo("Z1234"),
          eqTo("2026"),
          eqTo("5"),
          eqTo(failedUploadResult)
        )
      ).thenReturn(Future.successful(()))

      val result = monthlyReturnUpscanCallback(Json.toJson(failedUploadResult))

      status(result) mustBe ACCEPTED
      verify(mockUpscanCallbackService).monthlyReturnUpscanCallback(
        eqTo("Z1234"),
        eqTo("2026"),
        eqTo("5"),
        eqTo(failedUploadResult)
      )
    }

    "must return SERVICE_UNAVAILABLE when the callback service fails" in {
      when(
        mockUpscanCallbackService.monthlyReturnUpscanCallback(
          eqTo("Z1234"),
          eqTo("2026"),
          eqTo("5"),
          eqTo(successfulUploadResult)
        )
      ).thenReturn(Future.failed(new RuntimeException("mongodb down")))

      val result = monthlyReturnUpscanCallback(Json.toJson(successfulUploadResult))

      status(result) mustBe SERVICE_UNAVAILABLE
      verify(mockUpscanCallbackService).monthlyReturnUpscanCallback(
        eqTo("Z1234"),
        eqTo("2026"),
        eqTo("5"),
        eqTo(successfulUploadResult)
      )
    }

    "must return BAD_REQUEST when the payload has an unknown fileStatus" in {
      val result = monthlyReturnUpscanCallback(Json.toJson(InvalidFileStatusPayload(upscanReference, "SCANNING")))

      status(result) mustBe BAD_REQUEST
      contentAsString(result) must include("Invalid UpscanResult payload")
    }

    "must return BAD_REQUEST when the payload has an unknown failureReason" in {
      val result = monthlyReturnUpscanCallback(
        Json.toJson(
          InvalidFailureReasonPayload(
            upscanReference,
            "FAILED",
            InvalidFailureDetails("DUPLICATE", "Duplicate file")
          )
        )
      )

      status(result) mustBe BAD_REQUEST
      contentAsString(result) must include("Invalid UpscanResult payload")
    }
  }

  private def monthlyReturnUpscanCallback(requestBody: JsValue) =
    controller.monthlyReturnUpscanCallback(
      zReference = "Z1234",
      taxYear = "2026",
      month = "5"
    )(
      FakeRequest()
        .withBody(requestBody)
    )

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
