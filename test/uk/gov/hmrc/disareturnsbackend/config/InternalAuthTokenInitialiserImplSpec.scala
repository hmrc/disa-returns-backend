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

package uk.gov.hmrc.disareturnsbackend.config

import base.SpecBase
import com.typesafe.config.{Config, ConfigFactory}
import org.apache.pekko.Done
import org.apache.pekko.actor.ActorSystem
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.{never, times, verify, when}
import play.api.http.Status.{BAD_REQUEST, CREATED, INTERNAL_SERVER_ERROR, NOT_FOUND, OK, UNAUTHORIZED}
import play.api.libs.concurrent.Futures
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.http.client.{HttpClientV2, RequestBuilder}
import uk.gov.hmrc.http.{BadGatewayException, GatewayTimeoutException, HeaderCarrier, HttpResponse, StringContextOps, UpstreamErrorResponse}

import scala.concurrent.Future
import scala.concurrent.duration.{DurationInt, FiniteDuration}

class InternalAuthTokenInitialiserImplSpec extends SpecBase {

  private val internalAuthToken     = "valid-internal-auth-token"
  private val appName               = "disa-returns-backend"
  private val internalAuthService   = "http://localhost:8470"
  private val fullTokenUrl          = url"$internalAuthService/test-only/token"
  private val callAmountWithRetries = 4
  private val retryConfig: Config   =
    ConfigFactory.parseString("http-verbs.retries.intervals = [1 millisecond, 1 millisecond, 1 millisecond]")

  class TestFutures extends Futures {
    var timeoutDuration: Option[FiniteDuration] = None

    override def timeout[A](
      duration: FiniteDuration
    )(future: => Future[A]): Future[A] = {
      timeoutDuration = Some(duration)
      future
    }

    override def delayed[A](duration: FiniteDuration)(
      future: => Future[A]
    ): Future[A] = future

    override def delay(duration: FiniteDuration): Future[Done] =
      Future.successful(Done)
  }

  trait TestSetup {
    val mockAppConfig: AppConfig               = mock[AppConfig]
    val mockHttpClient: HttpClientV2           = mock[HttpClientV2]
    val mockGetRequestBuilder: RequestBuilder  = mock[RequestBuilder]
    val mockPostRequestBuilder: RequestBuilder = mock[RequestBuilder]
    val futures                                = new TestFutures

    lazy val initialiser =
      new InternalAuthTokenInitialiserImpl(
        inject[ActorSystem],
        mockAppConfig,
        retryConfig,
        mockHttpClient,
        futures
      )

    when(mockAppConfig.internalAuthService).thenReturn(internalAuthService)
    when(mockAppConfig.internalAuthToken).thenReturn(internalAuthToken)
    when(mockAppConfig.appName).thenReturn(appName)
    when(mockHttpClient.get(eqTo(fullTokenUrl))(any[HeaderCarrier]))
      .thenReturn(mockGetRequestBuilder)
    when(mockGetRequestBuilder.setHeader("Authorization" -> internalAuthToken))
      .thenReturn(mockGetRequestBuilder)

    def authTokenIsValidResponse(status: Int): Unit =
      when(mockGetRequestBuilder.execute[HttpResponse](any(), any()))
        .thenReturn(Future.successful(HttpResponse(status)))

    def authTokenIsValidFailure(exception: Throwable): Unit =
      when(mockGetRequestBuilder.execute[HttpResponse](any(), any()))
        .thenReturn(Future.failed(exception))

    def createAuthTokenResponse(result: Either[UpstreamErrorResponse, HttpResponse]): Unit = {
      when(mockHttpClient.post(eqTo(fullTokenUrl))(any[HeaderCarrier]))
        .thenReturn(mockPostRequestBuilder)
      when(
        mockPostRequestBuilder.withBody(any[JsObject]())(any(), any(), any())
      )
        .thenReturn(mockPostRequestBuilder)
      when(mockPostRequestBuilder.execute[Either[UpstreamErrorResponse, HttpResponse]](any(), any()))
        .thenReturn(Future.successful(result))
    }

    def createAuthTokenFailure(exception: Exception): Unit = {
      when(mockHttpClient.post(eqTo(fullTokenUrl))(any[HeaderCarrier]))
        .thenReturn(mockPostRequestBuilder)
      when(
        mockPostRequestBuilder.withBody(any[JsObject]())(any(), any(), any())
      )
        .thenReturn(mockPostRequestBuilder)
      when(mockPostRequestBuilder.execute[Either[UpstreamErrorResponse, HttpResponse]](any(), any()))
        .thenReturn(Future.failed(exception))
    }
  }

  "InternalAuthTokenInitialiserImpl" - {

    val expectedCreateTokenRequestBody: JsObject =
      Json.obj(
        "token"       -> internalAuthToken,
        "principal"   -> appName,
        "permissions" -> Seq(
          Json.obj(
            "resourceType"     -> "object-store",
            "resourceLocation" -> "disa-returns-backend",
            "actions"          -> List("READ", "WRITE", "DELETE")
          ),
          Json.obj(
            "resourceType"     -> "disa-returns-submission",
            "resourceLocation" -> "*",
            "actions"          -> List("READ", "WRITE")
          )
        )
      )

    "initialised" - {
      "must return Done when the auth token is already valid" in new TestSetup {
        authTokenIsValidResponse(OK)

        initialiser.initialised.futureValue mustBe Done
        initialiser.initialised.futureValue mustBe Done

        futures.timeoutDuration mustBe Some(30.seconds)
        verify(mockGetRequestBuilder).execute[HttpResponse](any(), any())
        verify(mockHttpClient, never()).post(any())(any())
      }

      Seq(NOT_FOUND, UNAUTHORIZED).foreach { status =>
        s"must create the auth token when the existing token check returns $status" in new TestSetup {
          authTokenIsValidResponse(status)
          createAuthTokenResponse(Right(HttpResponse(CREATED)))

          initialiser.initialised.futureValue mustBe Done

          futures.timeoutDuration mustBe Some(30.seconds)
          verify(mockPostRequestBuilder)
            .withBody(eqTo(expectedCreateTokenRequestBody))(any(), any(), any())
          verify(mockPostRequestBuilder).execute[Either[UpstreamErrorResponse, HttpResponse]](any(), any())
        }

      }

      "must fail without retrying when the auth endpoint returns an unexpected non-error status" in new TestSetup {
        authTokenIsValidResponse(NOT_FOUND)
        createAuthTokenResponse(Right(HttpResponse(OK)))

        val thrown: Throwable = initialiser.initialised.failed.futureValue

        futures.timeoutDuration mustBe Some(30.seconds)
        thrown mustBe a[RuntimeException]
        thrown.getMessage mustBe "Failed to initialise internal-auth token"
        verify(mockPostRequestBuilder).execute[Either[UpstreamErrorResponse, HttpResponse]](any(), any())
      }

      Seq(
        "BadGatewayException"     -> new BadGatewayException("Bad gateway"),
        "GatewayTimeoutException" -> new GatewayTimeoutException("Gateway timeout")
      ).foreach { case (exceptionType, exception) =>
        s"must retry when creating the auth token fails with a $exceptionType" in new TestSetup {
          authTokenIsValidResponse(NOT_FOUND)
          createAuthTokenFailure(exception)

          initialiser.initialised.failed.futureValue mustBe exception

          verify(mockPostRequestBuilder, times(callAmountWithRetries))
            .execute[Either[UpstreamErrorResponse, HttpResponse]](any(), any())
        }
      }

      Seq(
        "4xx" -> UpstreamErrorResponse("Bad request", BAD_REQUEST),
        "5xx" -> UpstreamErrorResponse("Internal server error", INTERNAL_SERVER_ERROR)
      ).foreach { case (statusRange, error) =>
        s"must retry when creating the auth token returns a $statusRange UpstreamErrorResponse" in new TestSetup {
          authTokenIsValidResponse(NOT_FOUND)
          createAuthTokenResponse(Left(error))

          initialiser.initialised.failed.futureValue mustBe error

          verify(mockPostRequestBuilder, times(callAmountWithRetries))
            .execute[Either[UpstreamErrorResponse, HttpResponse]](any(), any())
        }
      }

      Seq(
        "BadGatewayException"       -> new BadGatewayException("Bad gateway"),
        "GatewayTimeoutException"   -> new GatewayTimeoutException("Gateway timeout"),
        "4xx UpstreamErrorResponse" -> UpstreamErrorResponse("Bad request", BAD_REQUEST),
        "5xx UpstreamErrorResponse" -> UpstreamErrorResponse("Internal server error", INTERNAL_SERVER_ERROR)
      ).foreach { case (errorType, error) =>
        s"must exhaust retries without creating a token when checking fails with $errorType" in new TestSetup {
          authTokenIsValidFailure(error)

          initialiser.initialised.failed.futureValue mustBe error

          verify(mockGetRequestBuilder, times(callAmountWithRetries)).execute[HttpResponse](any(), any())
          verify(mockHttpClient, never()).post(any())(any())
        }

        Seq(OK, NOT_FOUND).foreach { status =>
          s"must recover from $errorType when a subsequent token check returns $status" in new TestSetup {
            when(mockGetRequestBuilder.execute[HttpResponse](any(), any()))
              .thenReturn(Future.failed(error), Future.successful(HttpResponse(status)))
            createAuthTokenResponse(Right(HttpResponse(CREATED)))

            initialiser.initialised.futureValue mustBe Done

            verify(mockGetRequestBuilder, times(2)).execute[HttpResponse](any(), any())
            verify(mockHttpClient, times(if (status == OK) 0 else 1)).post(eqTo(fullTokenUrl))(any[HeaderCarrier])
          }
        }
      }

      "must retry server error responses without creating a token" in new TestSetup {
        authTokenIsValidResponse(INTERNAL_SERVER_ERROR)

        val error = initialiser.initialised.failed.futureValue
        error mustBe a[UpstreamErrorResponse]
        error.asInstanceOf[UpstreamErrorResponse].statusCode mustBe INTERNAL_SERVER_ERROR
        verify(mockGetRequestBuilder, times(callAmountWithRetries)).execute[HttpResponse](any(), any())
        verify(mockHttpClient, never()).post(any())(any())
      }

      "must recover when a server error response is followed by a valid token" in new TestSetup {
        when(mockGetRequestBuilder.execute[HttpResponse](any(), any()))
          .thenReturn(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR)), Future.successful(HttpResponse(OK)))

        initialiser.initialised.futureValue mustBe Done

        verify(mockGetRequestBuilder, times(2)).execute[HttpResponse](any(), any())
        verify(mockHttpClient, never()).post(any())(any())
      }

      "must fail without retrying when checking the auth token fails with an unrelated exception" in new TestSetup {
        val exception = new RuntimeException("Unable to check auth token")

        authTokenIsValidFailure(exception)

        initialiser.initialised.failed.futureValue mustBe exception
        futures.timeoutDuration mustBe Some(30.seconds)
        verify(mockGetRequestBuilder).execute[HttpResponse](any(), any())
        verify(mockHttpClient, never()).post(any())(any())
      }
    }
  }
}
