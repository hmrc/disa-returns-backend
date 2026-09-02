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
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.{verify, when}
import play.api.http.HeaderNames.AUTHORIZATION
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.auth.core.{AuthConnector, Enrolment, EnrolmentIdentifier, Enrolments, MissingBearerToken}
import uk.gov.hmrc.disareturnsbackend.connectors.ReturnsSubmissionConnector
import uk.gov.hmrc.disareturnsbackend.controllers.actions.RequestAuthAndValidationActionImpl
import uk.gov.hmrc.disareturnsbackend.services.TimeSource

import scala.concurrent.Future

class ReportingWindowControllerSpec extends SpecBase {
  private val authConnector                  = mock[AuthConnector]
  private val returnsSubmissionConnector     = mock[ReturnsSubmissionConnector]
  private val requestAuthAndValidationAction =
    new RequestAuthAndValidationActionImpl(stubControllerComponents(), authConnector, mock[TimeSource])
  private val controller                     = new ReportingWindowController(
    stubControllerComponents(),
    requestAuthAndValidationAction,
    returnsSubmissionConnector
  )

  "ReportingWindowController" - {
    "must normalize the Z-reference and return submission's status" in {
      authoriseWith(testZReference)
      when(returnsSubmissionConnector.isReportingWindowOpen(eqTo(testZReference))(any(), any()))
        .thenReturn(Future.successful(true))

      val result = controller.status(s" $lowercaseTestZReference ")(authorisedRequest)

      status(result) mustBe OK
      contentAsJson(result) mustBe Json.obj("reportingWindowOpen" -> true)
      verify(returnsSubmissionConnector).isReportingWindowOpen(eqTo(testZReference))(any(), any())
    }

    "must return BadRequest for an invalid Z-reference" in {
      authoriseWith(testZReference)

      status(controller.status(invalidTestZReference)(authorisedRequest)) mustBe BAD_REQUEST
    }

    "must return Unauthorized when bearer authentication fails" in {
      when(authConnector.authorise[Enrolments](any(), any())(any(), any()))
        .thenReturn(Future.failed(MissingBearerToken()))

      status(controller.status(testZReference)(FakeRequest())) mustBe UNAUTHORIZED
    }

    "must return Forbidden without an activated matching enrolment" in {
      authoriseWith(testZReference, state = "NotYetActivated")

      status(controller.status(testZReference)(authorisedRequest)) mustBe FORBIDDEN
    }

    "must return ServiceUnavailable when submission fails" in {
      authoriseWith(testZReference)
      when(returnsSubmissionConnector.isReportingWindowOpen(eqTo(testZReference))(any(), any()))
        .thenReturn(Future.failed(new RuntimeException("submission unavailable")))

      status(controller.status(testZReference)(authorisedRequest)) mustBe SERVICE_UNAVAILABLE
    }
  }

  private def authorisedRequest =
    FakeRequest(GET, "/reporting-window/status").withHeaders(AUTHORIZATION -> testBearerToken)

  private def authoriseWith(zReference: String, state: String = "Activated"): Unit =
    when(authConnector.authorise[Enrolments](any(), any())(any(), any()))
      .thenReturn(
        Future.successful(
          Enrolments(
            Set(
              Enrolment(
                key = "HMRC-DISA-ORG",
                identifiers = Seq(EnrolmentIdentifier("ZREF", zReference)),
                state = state
              )
            )
          )
        )
      )
}
