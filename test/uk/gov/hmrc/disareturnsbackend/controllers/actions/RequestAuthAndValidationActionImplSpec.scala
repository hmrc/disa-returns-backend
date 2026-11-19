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
import ch.qos.logback.classic.{Level, Logger}
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.slf4j.LoggerFactory
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.*
import org.scalatest.BeforeAndAfterEach
import play.api.http.HeaderNames.AUTHORIZATION
import play.api.mvc.Result
import play.api.mvc.Results.Ok
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.auth.core.*
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.disareturnsbackend.config.AppConfig
import uk.gov.hmrc.disareturnsbackend.connectors.DisaAccountConnector
import uk.gov.hmrc.disareturnsbackend.utils.TimeSource
import uk.gov.hmrc.http.{BadGatewayException, GatewayTimeoutException, HeaderCarrier, UpstreamErrorResponse}

import java.time.Instant
import scala.concurrent.Future
import scala.jdk.CollectionConverters.*

class RequestAuthAndValidationActionImplSpec extends SpecBase with BeforeAndAfterEach {

  private val mockAuthConnector        = mock[AuthConnector]
  private val mockAppConfig            = mock[AppConfig]
  private val mockDisaAccountConnector = mock[DisaAccountConnector]
  private val authAction               = authActionAt()

  private def authActionAt(now: String = "2026-06-07T12:00:00Z") =
    new RequestAuthAndValidationActionImpl(
      stubControllerComponents(),
      mockAuthConnector,
      new TimeSource {
        override def instant(zReference: String)(implicit hc: HeaderCarrier): Future[Instant] =
          Future.successful(Instant.parse(now))
      },
      mockAppConfig,
      mockDisaAccountConnector
    )

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockAuthConnector, mockAppConfig, mockDisaAccountConnector)
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
            disaEnrolment(wrongTestZReference),
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
      authoriseWith(disaEnrolments(wrongTestZReference))

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
      val result = authAction(testZReference, "2026-27", "5", checkPeriod = true)
        .async(_ => Future.successful(Ok))(authorisedRequest)

      status(result) mustBe OK
    }

    "must reject the current monthly period when period checking is enabled" in {
      val result = authAction(testZReference, "2026-27", "6", checkPeriod = true)
        .async(_ => Future.successful(Ok))(authorisedRequest)

      status(result) mustBe UNPROCESSABLE_ENTITY
    }

    "must reject the previous month when the tax year does not match and period checking is enabled" in {
      val result = authAction(testZReference, "2025-26", "5", checkPeriod = true)
        .async(_ => Future.successful(Ok))(authorisedRequest)

      status(result) mustBe UNPROCESSABLE_ENTITY
    }

    "must calculate April as the start of a tax year when period checking is enabled" in {
      val result = authActionAt("2026-05-07T12:00:00Z")(testZReference, "2026-27", "4", checkPeriod = true)
        .async(_ => Future.successful(Ok))(authorisedRequest)

      status(result) mustBe OK
    }

    "must calculate March as the end of the previous tax year when period checking is enabled" in {
      val result = authActionAt("2026-04-07T12:00:00Z")(testZReference, "2025-26", "3", checkPeriod = true)
        .async(_ => Future.successful(Ok))(authorisedRequest)

      status(result) mustBe OK
    }

    "must return SERVICE_UNAVAILABLE when the time source fails during period checking" in {
      val timeSource = mock[TimeSource]
      when(timeSource.instant(eqTo(testZReference))(any()))
        .thenReturn(Future.failed(new RuntimeException("time source unavailable")))
      val action     = new RequestAuthAndValidationActionImpl(
        stubControllerComponents(),
        mockAuthConnector,
        timeSource,
        mockAppConfig,
        mockDisaAccountConnector
      )

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
      Seq(disaEnrolments(wrongTestZReference), disaEnrolments(testZReference, state = "NotYetActivated")).foreach {
        enrolments =>
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

  Seq("monthly return", "reporting window").foreach { endpoint =>
    s"Agent authentication for $endpoint" - {
      def invoke(zReference: String = testZReference, checkPeriod: Boolean = false)(
        block: => Future[Result]
      ): Future[Result] =
        if (endpoint == "monthly return") {
          authAction(zReference, testTaxYear, testRouteMonth, checkPeriod).async(_ => block)(authorisedRequest)
        } else {
          authAction(zReference).async(_ => block)(authorisedRequest)
        }

      Seq(false, true).foreach { matchingEnrolment =>
        s"must reject a disabled agent with matching principal enrolment=$matchingEnrolment without calling preferences" in {
          authoriseWith(
            if (matchingEnrolment) disaEnrolments(testZReference) else Enrolments(Set.empty),
            Some(AffinityGroup.Agent)
          )
          var executed = false

          val result = invoke() { executed = true; Future.successful(Ok) }

          status(result) mustBe FORBIDDEN
          executed mustBe false
          verifyNoInteractions(mockDisaAccountConnector)
        }
      }

      "must authorise an enabled agent without a principal enrolment using the original bearer token" in {
        when(mockAppConfig.agentAuthenticationEnabled).thenReturn(true)
        authoriseWith(Enrolments(Set.empty), Some(AffinityGroup.Agent))
        when(mockDisaAccountConnector.getAgentZReference()(any()))
          .thenReturn(Future.successful(lowercaseTestZReference))

        status(invoke(lowercaseTestZReference)(Future.successful(Ok))) mustBe OK

        verify(mockDisaAccountConnector).getAgentZReference()(
          org.mockito.ArgumentMatchers.argThat[HeaderCarrier](hc => hc.authorization.exists(_.value == testBearerToken))
        )
        verify(mockAuthConnector, times(1)).authorise[Enrolments ~ Option[AffinityGroup]](any(), any())(any(), any())
      }

      "must reject a different preference even when the agent has a matching principal enrolment" in {
        when(mockAppConfig.agentAuthenticationEnabled).thenReturn(true)
        authoriseWith(disaEnrolments(testZReference), Some(AffinityGroup.Agent))
        when(mockDisaAccountConnector.getAgentZReference()(any())).thenReturn(Future.successful(wrongTestZReference))
        var executed = false

        status(invoke() { executed = true; Future.successful(Ok) }) mustBe FORBIDDEN
        executed mustBe false
      }

      Seq(
        ("401", UpstreamErrorResponse("unauthenticated", UNAUTHORIZED), UNAUTHORIZED),
        ("403", UpstreamErrorResponse("delegated access denied", FORBIDDEN), FORBIDDEN),
        ("404", UpstreamErrorResponse("missing or expired preferences", NOT_FOUND), FORBIDDEN),
        ("503", UpstreamErrorResponse("unavailable", SERVICE_UNAVAILABLE), SERVICE_UNAVAILABLE),
        ("unexpected 400", UpstreamErrorResponse("bad request", BAD_REQUEST), SERVICE_UNAVAILABLE),
        ("unexpected 500", UpstreamErrorResponse("internal error", INTERNAL_SERVER_ERROR), SERVICE_UNAVAILABLE),
        ("connection failure", new BadGatewayException("connection failed"), SERVICE_UNAVAILABLE),
        ("timeout", new GatewayTimeoutException("timed out"), SERVICE_UNAVAILABLE),
        ("malformed response", new IllegalArgumentException("invalid preferences"), SERVICE_UNAVAILABLE)
      ).foreach { case (description, error, expectedStatus) =>
        s"must handle $description without executing the protected operation" in {
          when(mockAppConfig.agentAuthenticationEnabled).thenReturn(true)
          authoriseWith(Enrolments(Set.empty), Some(AffinityGroup.Agent))
          when(mockDisaAccountConnector.getAgentZReference()(any())).thenReturn(Future.failed(error))
          var executed = false

          status(invoke() { executed = true; Future.successful(Ok) }) mustBe expectedStatus
          executed mustBe false
        }
      }

      Seq(
        (FORBIDDEN, "403 Forbidden: authorisation denied"),
        (NOT_FOUND, "404 Not Found: preferences missing or expired")
      ).foreach { case (upstreamStatus, message) =>
        s"must distinguish upstream $upstreamStatus in warning logs" in {
          when(mockAppConfig.agentAuthenticationEnabled).thenReturn(true)
          authoriseWith(Enrolments(Set.empty), Some(AffinityGroup.Agent))
          when(mockDisaAccountConnector.getAgentZReference()(any()))
            .thenReturn(Future.failed(UpstreamErrorResponse("preferences failed", upstreamStatus)))
          val logger        = LoggerFactory.getLogger(classOf[RequestAuthAndValidationActionImpl]).asInstanceOf[Logger]
          val previousLevel = logger.getLevel
          logger.setLevel(Level.WARN)
          val appender      = new ListAppender[ILoggingEvent]()
          appender.start()
          logger.addAppender(appender)
          try {
            status(invoke()(Future.successful(Ok))) mustBe FORBIDDEN
            appender.list.asScala.exists(event =>
              event.getLevel.toString == "WARN" && event.getFormattedMessage.contains(message)
            ) mustBe true
          } finally {
            logger.setLevel(previousLevel)
            logger.detachAppender(appender)
            appender.stop()
          }
        }
      }

      Seq(MissingBearerToken(), InvalidBearerToken(), BearerTokenExpired()).foreach { error =>
        s"must return 401 for ${error.getClass.getSimpleName} without calling preferences" in {
          when(mockAppConfig.agentAuthenticationEnabled).thenReturn(true)
          failAuthorisationWith(error)
          var executed = false

          status(invoke() { executed = true; Future.successful(Ok) }) mustBe UNAUTHORIZED
          executed mustBe false
          verifyNoInteractions(mockDisaAccountConnector)
        }
      }

      "must validate request parameters before calling preferences" in {
        when(mockAppConfig.agentAuthenticationEnabled).thenReturn(true)
        authoriseWith(Enrolments(Set.empty), Some(AffinityGroup.Agent))
        var executed = false

        status(invoke(invalidTestZReference) { executed = true; Future.successful(Ok) }) mustBe BAD_REQUEST
        executed mustBe false
        verifyNoInteractions(mockDisaAccountConnector)
      }

      Seq(false, true).foreach { enabled =>
        Seq(Some(AffinityGroup.Organisation), None).foreach { affinity =>
          s"must preserve ISA manager authentication with flag=$enabled and affinity=$affinity without calling preferences" in {
            when(mockAppConfig.agentAuthenticationEnabled).thenReturn(enabled)
            authoriseWith(disaEnrolments(testZReference), affinity)

            status(invoke()(Future.successful(Ok))) mustBe OK
            verifyNoInteractions(mockDisaAccountConnector)
          }
        }
      }

      if (endpoint == "monthly return") {
        "must preserve period validation before calling preferences" in {
          when(mockAppConfig.agentAuthenticationEnabled).thenReturn(true)
          authoriseWith(Enrolments(Set.empty), Some(AffinityGroup.Agent))
          val action = authActionAt("2026-07-07T12:00:00Z")

          status(
            action(testZReference, testTaxYear, testRouteMonth, checkPeriod = true)
              .async(_ => Future.successful(Ok))(authorisedRequest)
          ) mustBe UNPROCESSABLE_ENTITY
          verifyNoInteractions(mockDisaAccountConnector)
        }
      }
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

  private def authoriseWith(
    enrolments: Enrolments,
    affinityGroup: Option[AffinityGroup] = Some(AffinityGroup.Organisation)
  ): Unit =
    when(mockAuthConnector.authorise[Enrolments ~ Option[AffinityGroup]](any(), any())(any(), any()))
      .thenReturn(Future.successful(new ~(enrolments, affinityGroup)))

  private def failAuthorisationWith(exception: Throwable): Unit =
    when(mockAuthConnector.authorise[Enrolments ~ Option[AffinityGroup]](any(), any())(any(), any()))
      .thenReturn(Future.failed(exception))
}
