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
import org.apache.pekko.Done
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.{verify, when}
import play.api.http.Status.{CREATED, INTERNAL_SERVER_ERROR, NOT_FOUND, OK}
import play.api.libs.concurrent.Futures
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.http.client.{HttpClientV2, RequestBuilder}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.Future

class InternalAuthTokenInitialiserImplSpec extends SpecBase {

  private val internalAuthToken   = "valid-internal-auth-token"
  private val appName             = "disa-returns-backend"
  private val internalAuthService = "http://localhost:8470"
  private val fullTokenUrl        = url"$internalAuthService/test-only/token"

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
        mockAppConfig,
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

    def createAuthTokenResponse(status: Int): Unit = {
      when(mockHttpClient.post(eqTo(fullTokenUrl))(any[HeaderCarrier]))
        .thenReturn(mockPostRequestBuilder)
      when(
        mockPostRequestBuilder.withBody(any[JsObject]())(any(), any(), any())
      )
        .thenReturn(mockPostRequestBuilder)
      when(mockPostRequestBuilder.execute[HttpResponse](any(), any()))
        .thenReturn(Future.successful(HttpResponse(status)))
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
      }

      "must create the auth token when the existing token is not valid" in new TestSetup {
        authTokenIsValidResponse(NOT_FOUND)
        createAuthTokenResponse(CREATED)

        initialiser.initialised.futureValue mustBe Done

        futures.timeoutDuration mustBe Some(30.seconds)
        verify(mockPostRequestBuilder)
          .withBody(eqTo(expectedCreateTokenRequestBody))(any(), any(), any())
        verify(mockPostRequestBuilder).execute[HttpResponse](any(), any())
      }

      "must fail when the auth token cannot be created" in new TestSetup {
        authTokenIsValidResponse(NOT_FOUND)
        createAuthTokenResponse(INTERNAL_SERVER_ERROR)

        val thrown: Throwable = initialiser.initialised.failed.futureValue

        futures.timeoutDuration mustBe Some(30.seconds)
        thrown mustBe a[RuntimeException]
        thrown.getMessage mustBe "Unable to initialise internal-auth token"
      }

      "must fail when checking the auth token fails" in new TestSetup {
        val exception = new RuntimeException("Unable to check auth token")

        authTokenIsValidFailure(exception)

        initialiser.initialised.failed.futureValue mustBe exception
        futures.timeoutDuration mustBe Some(30.seconds)
      }
    }
  }
}
