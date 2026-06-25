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
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.disareturnsbackend.models.*
import uk.gov.hmrc.disareturnsbackend.services.UpscanCallbackService

import scala.concurrent.Future

class UpscanCallbackControllerSpec extends SpecBase with BeforeAndAfterEach {

  private val mockUpscanCallbackService = mock[UpscanCallbackService]

  override lazy val app: Application = applicationBuilder(
    Seq(
      bind[UpscanCallbackService].toInstance(mockUpscanCallbackService)
    )
  ).build()

  private lazy val controller = inject[UpscanCallbackController]

  private val zReference = testZReference
  private val taxYear    = testTaxYear
  private val month      = testMonth
  private val routeMonth = testRouteMonth

  private val upscanReference = testUploadReference

  private val uploadDetails = UpscanDetails(
    fileName = testFileName,
    fileMimeType = testFileMimeType,
    uploadTimestamp = testCreatedOn,
    checksum = testChecksum,
    size = testFileSize
  )

  private val successfulUploadResult: UpscanResult = UpscanSuccess(
    reference = upscanReference,
    downloadUrl = testDownloadUrl,
    uploadDetails = uploadDetails
  )

  private val failedUploadResult: UpscanResult = UpscanFailure(
    reference = upscanReference,
    failureDetails = UpscanFailureDetails(
      failureReason = UpscanFailureReason.Rejected,
      message = testMimeTypeFailureMessage
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
          eqTo(zReference),
          eqTo(taxYear),
          eqTo(month),
          eqTo(successfulUploadResult)
        )
      ).thenReturn(Future.successful(()))

      val result = monthlyReturnUpscanCallback(Json.toJson(successfulUploadResult))

      status(result) mustBe ACCEPTED
      verify(mockUpscanCallbackService).monthlyReturnUpscanCallback(
        eqTo(zReference),
        eqTo(taxYear),
        eqTo(month),
        eqTo(successfulUploadResult)
      )
    }

    "must return ACCEPTED for valid FAILED callback payload" in {
      when(
        mockUpscanCallbackService.monthlyReturnUpscanCallback(
          eqTo(zReference),
          eqTo(taxYear),
          eqTo(month),
          eqTo(failedUploadResult)
        )
      ).thenReturn(Future.successful(()))

      val result = monthlyReturnUpscanCallback(Json.toJson(failedUploadResult))

      status(result) mustBe ACCEPTED
      verify(mockUpscanCallbackService).monthlyReturnUpscanCallback(
        eqTo(zReference),
        eqTo(taxYear),
        eqTo(month),
        eqTo(failedUploadResult)
      )
    }

    "must return SERVICE_UNAVAILABLE when the callback service fails" in {
      when(
        mockUpscanCallbackService.monthlyReturnUpscanCallback(
          eqTo(zReference),
          eqTo(taxYear),
          eqTo(month),
          eqTo(successfulUploadResult)
        )
      ).thenReturn(Future.failed(new RuntimeException(testMongoDownMessage)))

      val result = monthlyReturnUpscanCallback(Json.toJson(successfulUploadResult))

      status(result) mustBe SERVICE_UNAVAILABLE
      verify(mockUpscanCallbackService).monthlyReturnUpscanCallback(
        eqTo(zReference),
        eqTo(taxYear),
        eqTo(month),
        eqTo(successfulUploadResult)
      )
    }

    "must return BAD_REQUEST when the payload has an unknown fileStatus" in {
      val result =
        monthlyReturnUpscanCallback(Json.toJson(InvalidFileStatusPayload(upscanReference, invalidFileStatusString)))

      status(result) mustBe BAD_REQUEST
      contentAsString(result) must include(invalidUpscanResultPayloadMessage)
    }

    "must return BAD_REQUEST when the payload has an unknown failureReason" in {
      val result = monthlyReturnUpscanCallback(
        Json.toJson(
          InvalidFailureReasonPayload(
            upscanReference,
            failedFileStatusString,
            InvalidFailureDetails(invalidFailureReasonString, testDuplicateFileMessage)
          )
        )
      )

      status(result) mustBe BAD_REQUEST
      contentAsString(result) must include(invalidUpscanResultPayloadMessage)
    }

    "must return BAD_REQUEST when path parameters are invalid" in {
      val result = monthlyReturnUpscanCallback(
        requestBody = Json.toJson(successfulUploadResult),
        zReference = invalidTestZReference,
        taxYear = testTaxYear,
        month = routeMonth
      )

      status(result) mustBe BAD_REQUEST
      contentAsString(result) must include(zReferenceFieldName)
    }
  }

  private def monthlyReturnUpscanCallback(requestBody: JsValue): Future[Result] =
    monthlyReturnUpscanCallback(requestBody, zReference, taxYear, routeMonth)

  private def monthlyReturnUpscanCallback(
    requestBody: JsValue,
    zReference: String,
    taxYear: String,
    month: String
  ): Future[Result] =
    controller.monthlyReturnUpscanCallback(
      zReference = zReference,
      taxYear = taxYear,
      month = month
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
