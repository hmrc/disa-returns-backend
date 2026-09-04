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

package uk.gov.hmrc.disareturnsbackend.controllers.actions

import base.SpecBase
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import org.scalatest.BeforeAndAfterEach
import play.api.http.HeaderNames.AUTHORIZATION
import play.api.mvc.Results.Ok
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.auth.core.{AuthConnector, BearerTokenExpired, Enrolment, EnrolmentIdentifier, Enrolments, InternalError, InvalidBearerToken, MissingBearerToken}
import uk.gov.hmrc.disareturnsbackend.services.TimeSource
import uk.gov.hmrc.http.HeaderCarrier

import java.time.Instant
import scala.concurrent.Future

class RequestAuthAndValidationActionSpec extends SpecBase with BeforeAndAfterEach {

  private val mockAuthConnector = mock[AuthConnector]
  private val authAction        = authActionAt()

  private def authActionAt(now: String = "2026-06-07T12:00:00Z") =
    new RequestAuthAndValidationActionImpl(
      stubControllerComponents(),
      mockAuthConnector,
      new TimeSource {
        override def instant(zReference: String)(implicit hc: HeaderCarrier): Future[Instant] =
          Future.successful(Instant.parse(now))
      }
    )

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockAuthConnector)
    authoriseWith(disaEnrolments(testZReference))
  }

  "RequestAuthAndValidationAction" - {

    "must allow the request when the bearer token is valid and the DISA enrolment matches the zReference" in {
      val result = authAction(lowercaseTestZReference, testTaxYear, testRouteMonth)
        .async { request =>
          Future.successful(Ok(request.zReference))
        }(authorisedRequest)

      status(result) mustBe OK
      contentAsString(result) mustBe testZReference
    }

    "must allow the request when one of multiple DISA enrolments matches the zReference" in {
      authoriseWith(
        Enrolments(
          Set(
            disaEnrolment("Z9999"),
            disaEnrolment(testZReference)
          )
        )
      )

      val result = authAction(testZReference, testTaxYear, testRouteMonth)
        .async { request =>
          Future.successful(Ok(request.zReference))
        }(authorisedRequest)

      status(result) mustBe OK
      contentAsString(result) mustBe testZReference
    }

    "must return FORBIDDEN when the bearer token is valid but the DISA enrolment does not match the zReference" in {
      authoriseWith(disaEnrolments("Z9999"))

      val result = authAction(testZReference, testTaxYear, testRouteMonth)
        .async(_ => Future.successful(Ok))(authorisedRequest)

      status(result) mustBe FORBIDDEN
    }

    "must return FORBIDDEN when the bearer token has a matching DISA enrolment that is not activated" in {
      authoriseWith(disaEnrolments(testZReference, state = "NotYetActivated"))

      val result = authAction(testZReference, testTaxYear, testRouteMonth)
        .async(_ => Future.successful(Ok))(authorisedRequest)

      status(result) mustBe FORBIDDEN
    }

    "must return UNAUTHORIZED when the bearer token is missing" in {
      failAuthorisationWith(MissingBearerToken())

      val result = authAction(testZReference, testTaxYear, testRouteMonth)
        .async(_ => Future.successful(Ok))(FakeRequest("GET", "/test"))

      status(result) mustBe UNAUTHORIZED
    }

    "must return UNAUTHORIZED when the bearer token is invalid" in {
      failAuthorisationWith(InvalidBearerToken())

      val result = authAction(testZReference, testTaxYear, testRouteMonth)
        .async(_ => Future.successful(Ok))(authorisedRequest)

      status(result) mustBe UNAUTHORIZED
    }

    "must return UNAUTHORIZED when the bearer token has expired" in {
      failAuthorisationWith(BearerTokenExpired())

      val result = authAction(testZReference, testTaxYear, testRouteMonth)
        .async(_ => Future.successful(Ok))(authorisedRequest)

      status(result) mustBe UNAUTHORIZED
    }

    "must return SERVICE_UNAVAILABLE when auth returns an internal error" in {
      failAuthorisationWith(InternalError())

      val result = authAction(testZReference, testTaxYear, testRouteMonth)
        .async(_ => Future.successful(Ok))(authorisedRequest)

      status(result) mustBe SERVICE_UNAVAILABLE
    }

    "must return BAD_REQUEST when path parameters are invalid after successful bearer validation" in {
      val result = authAction(invalidTestZReference, testTaxYear, testRouteMonth)
        .async(_ => Future.successful(Ok))(authorisedRequest)

      status(result) mustBe BAD_REQUEST
      contentAsString(result) must include(zReferenceFieldName)
    }

    "must allow the previous monthly period when period checking is enabled" in {
      val result = authAction("Z1234", "2026-27", "5", checkPeriod = true)
        .async(_ => Future.successful(Ok))(authorisedRequest)

      status(result) mustBe OK
    }

    "must reject the current monthly period when period checking is enabled" in {
      val result = authAction("Z1234", "2026-27", "6", checkPeriod = true)
        .async(_ => Future.successful(Ok))(authorisedRequest)

      status(result) mustBe UNPROCESSABLE_ENTITY
    }

    "must reject the previous month when the tax year does not match and period checking is enabled" in {
      val result = authAction("Z1234", "2025-26", "5", checkPeriod = true)
        .async(_ => Future.successful(Ok))(authorisedRequest)

      status(result) mustBe UNPROCESSABLE_ENTITY
    }

    "must calculate April as the start of a tax year when period checking is enabled" in {
      val result = authActionAt("2026-05-07T12:00:00Z")("Z1234", "2026-27", "4", checkPeriod = true)
        .async(_ => Future.successful(Ok))(authorisedRequest)

      status(result) mustBe OK
    }

    "must calculate March as the end of the previous tax year when period checking is enabled" in {
      val result = authActionAt("2026-04-07T12:00:00Z")("Z1234", "2025-26", "3", checkPeriod = true)
        .async(_ => Future.successful(Ok))(authorisedRequest)

      status(result) mustBe OK
    }
  }

  private def authorisedRequest =
    FakeRequest("GET", "/test").withHeaders(AUTHORIZATION -> testBearerToken)

  private def disaEnrolments(zReference: String, state: String = "Activated"): Enrolments =
    Enrolments(
      Set(
        disaEnrolment(zReference, state)
      )
    )

  private def disaEnrolment(zReference: String, state: String = "Activated"): Enrolment =
    Enrolment(
      key = "HMRC-DISA-ORG",
      identifiers = Seq(EnrolmentIdentifier("ZREF", zReference)),
      state = state
    )

  private def authoriseWith(enrolments: Enrolments): Unit =
    when(mockAuthConnector.authorise[Enrolments](any(), any())(any(), any()))
      .thenReturn(Future.successful(enrolments))

  private def failAuthorisationWith(exception: Throwable): Unit =
    when(mockAuthConnector.authorise[Enrolments](any(), any())(any(), any()))
      .thenReturn(Future.failed(exception))
}
