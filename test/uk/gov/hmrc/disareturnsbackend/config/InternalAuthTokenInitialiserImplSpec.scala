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
import org.mockito.Mockito.{times, verify, when}
import play.api.http.Status.{BAD_REQUEST, CREATED, INTERNAL_SERVER_ERROR, OK}
import play.api.libs.concurrent.Futures
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.http.client.{HttpClientV2, RequestBuilder}
import uk.gov.hmrc.http.{BadGatewayException, GatewayTimeoutException, HeaderCarrier, HttpResponse, StringContextOps, UpstreamErrorResponse}

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.Future

class InternalAuthTokenInitialiserImplSpec extends SpecBase {

  private val internalAuthToken     = "valid-internal-auth-token-disa-returns-backend"
  private val appName               = "disa-returns-backend"
  private val internalAuthService   = "http://localhost:8470"
  private val fullTokenUrl          = url"$internalAuthService/test-only/token"
  private val timeoutDuration       = 30.seconds
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
      "must create or update the auth token with the required permissions" in new TestSetup {
        createAuthTokenResponse(Right(HttpResponse(CREATED)))

        initialiser.initialised.futureValue mustBe Done
        initialiser.initialised.futureValue mustBe Done

        futures.timeoutDuration mustBe Some(timeoutDuration)
        verify(mockPostRequestBuilder)
          .withBody(eqTo(expectedCreateTokenRequestBody))(any(), any(), any())
        verify(mockPostRequestBuilder).execute[Either[UpstreamErrorResponse, HttpResponse]](any(), any())
      }

      "must fail without retrying when the auth endpoint returns an unexpected non-error status" in new TestSetup {
        createAuthTokenResponse(Right(HttpResponse(OK)))

        val thrown: Throwable = initialiser.initialised.failed.futureValue

        futures.timeoutDuration mustBe Some(timeoutDuration)
        thrown mustBe a[RuntimeException]
        thrown.getMessage mustBe "Failed to initialise internal-auth token"
        verify(mockPostRequestBuilder).execute[Either[UpstreamErrorResponse, HttpResponse]](any(), any())
      }

      Seq(
        "BadGatewayException"     -> new BadGatewayException("Bad gateway"),
        "GatewayTimeoutException" -> new GatewayTimeoutException("Gateway timeout")
      ).foreach { case (exceptionType, exception) =>
        s"must retry when creating the auth token fails with a $exceptionType" in new TestSetup {
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
          createAuthTokenResponse(Left(error))

          initialiser.initialised.failed.futureValue mustBe error

          verify(mockPostRequestBuilder, times(callAmountWithRetries))
            .execute[Either[UpstreamErrorResponse, HttpResponse]](any(), any())
        }
      }
    }
  }
}
