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
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.*
import org.scalatest.BeforeAndAfterEach
import play.api.http.HeaderNames.AUTHORIZATION
import play.api.mvc.Results.Ok
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.auth.core.*
import uk.gov.hmrc.disareturnsbackend.utils.TimeSource
import uk.gov.hmrc.http.HeaderCarrier

import java.time.Instant
import scala.concurrent.Future

class RequestAuthAndValidationActionImplSpec extends SpecBase with BeforeAndAfterEach {

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

    "must default to no period check when used through the injected interface" in {
      val action: RequestAuthAndValidationAction = authActionAt("2027-06-07T12:00:00Z")

      val result = action(testZReference, testTaxYear, testRouteMonth)
        .async(_ => Future.successful(Ok))(authorisedRequest)

      status(result) mustBe OK
    }

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

    "must return SERVICE_UNAVAILABLE when the time source fails during period checking" in {
      val timeSource = mock[TimeSource]
      when(timeSource.instant(eqTo(testZReference))(any()))
        .thenReturn(Future.failed(new RuntimeException("time source unavailable")))
      val action     = new RequestAuthAndValidationActionImpl(stubControllerComponents(), mockAuthConnector, timeSource)

      val result = action(testZReference, testTaxYear, testRouteMonth, checkPeriod = true)
        .async(_ => Future.successful(Ok))(authorisedRequest)

      status(result) mustBe SERVICE_UNAVAILABLE
    }
  }

  "RequestAuthAction" - {

    "must trim and uppercase the Z-reference before invoking the action" in {
      val result = authAction(s" $lowercaseTestZReference ")
        .async(request => Future.successful(Ok(request.zReference)))(authorisedRequest)

      status(result) mustBe OK
      contentAsString(result) mustBe testZReference
    }

    "must reject an invalid Z-reference" in {
      val result = authAction(invalidTestZReference)
        .async(_ => Future.successful(Ok))(authorisedRequest)

      status(result) mustBe BAD_REQUEST
      contentAsString(result) must include(zReferenceFieldName)
    }

    "must reject an unmatched or inactive DISA enrolment" in
      Seq(disaEnrolments("Z9999"), disaEnrolments(testZReference, state = "NotYetActivated")).foreach { enrolments =>
        authoriseWith(enrolments)

        val result = authAction(testZReference).async(_ => Future.successful(Ok))(authorisedRequest)

        status(result) mustBe FORBIDDEN
      }

    "must reject a matching reference under a different identifier" in {
      authoriseWith(
        Enrolments(Set(Enrolment("HMRC-DISA-ORG", Seq(EnrolmentIdentifier("OTHER", testZReference)), "Activated")))
      )

      val result = authAction(testZReference).async(_ => Future.successful(Ok))(authorisedRequest)

      status(result) mustBe FORBIDDEN
    }

    "must return UNAUTHORIZED when authentication fails" in {
      failAuthorisationWith(InvalidBearerToken())

      val result = authAction(testZReference).async(_ => Future.successful(Ok))(authorisedRequest)

      status(result) mustBe UNAUTHORIZED
    }

    "must return SERVICE_UNAVAILABLE for an unexpected authentication failure" in {
      failAuthorisationWith(new RuntimeException("auth unavailable"))

      val result = authAction(testZReference).async(_ => Future.successful(Ok))(authorisedRequest)

      status(result) mustBe SERVICE_UNAVAILABLE
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
