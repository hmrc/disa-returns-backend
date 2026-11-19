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

package uk.gov.hmrc.disareturnsbackend.connectors

import base.TestConstants
import com.fasterxml.jackson.core.JsonParseException
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.*
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.Status.*
import play.api.libs.json.{JsNull, JsResultException, Json}
import uk.gov.hmrc.disareturnsbackend.config.AppConfig
import uk.gov.hmrc.http.client.{HttpClientV2, RequestBuilder}
import uk.gov.hmrc.http.{Authorization, HeaderCarrier, HttpResponse, StringContextOps, UpstreamErrorResponse}

import java.net.ConnectException
import java.util.concurrent.TimeoutException
import scala.concurrent.{ExecutionContext, Future}

class DisaAccountConnectorSpec
    extends AnyFreeSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with MockitoSugar
    with TestConstants {

  private implicit val ec: ExecutionContext = ExecutionContext.Implicits.global
  private implicit val hc: HeaderCarrier    = HeaderCarrier(authorization = Some(Authorization(testBearerToken)))
  private val serviceUrl                    = "http://disa-account"
  private val preferencesUrl                = url"$serviceUrl/disa-account/agents/preferences"

  private trait TestSetup {
    val mockHttpClient: HttpClientV2       = mock[HttpClientV2]
    val mockRequestBuilder: RequestBuilder = mock[RequestBuilder]
    val mockAppConfig: AppConfig           = mock[AppConfig]

    when(mockAppConfig.disaAccountService).thenReturn(serviceUrl)
    when(mockHttpClient.get(eqTo(preferencesUrl))(eqTo(hc))).thenReturn(mockRequestBuilder)

    val connector = new DisaAccountConnector(mockHttpClient, mockAppConfig)

    def respond(status: Int, body: String): Unit =
      when(mockRequestBuilder.execute[HttpResponse](any(), any()))
        .thenReturn(Future.successful(HttpResponse(status, body = body)))
  }

  "DisaAccountConnector.getAgentZReference" - {
    Seq(testZReference, lowercaseTestZReference).foreach { reference =>
      s"must return valid reference $reference using the preferences URL and original HeaderCarrier" in new TestSetup {
        respond(OK, Json.obj("zReference" -> reference).toString())

        connector.getAgentZReference().futureValue mustBe reference

        verify(mockHttpClient).get(eqTo(preferencesUrl))(eqTo(hc))
        verify(mockRequestBuilder).execute[HttpResponse](any(), any())
      }
    }

    Seq(
      "missing"    -> Json.obj(),
      "null"       -> Json.obj("zReference" -> JsNull),
      "non-string" -> Json.obj("zReference" -> 1),
      "non-object" -> Json.arr()
    ).foreach { case (description, body) =>
      s"must fail with a JSON validation error for a $description reference" in new TestSetup {
        respond(OK, body.toString())

        connector.getAgentZReference().failed.futureValue mustBe a[JsResultException]
      }
    }

    Seq(
      "empty"   -> "",
      "blank"   -> "  ",
      "invalid" -> invalidTestZReference,
      "padded"  -> s" $testZReference "
    ).foreach { case (description, reference) =>
      s"must reject a $description reference in a successful response" in new TestSetup {
        respond(OK, Json.obj("zReference" -> reference).toString())

        val error = connector.getAgentZReference().failed.futureValue

        error mustBe a[IllegalArgumentException]
        error.getMessage must include("Invalid ZReference in agent preferences response")
      }
    }

    "must fail with a JSON parsing error for a malformed successful response" in new TestSetup {
      respond(OK, "not-json")

      connector.getAgentZReference().failed.futureValue mustBe a[JsonParseException]
    }

    Seq(UNAUTHORIZED, FORBIDDEN, NOT_FOUND, SERVICE_UNAVAILABLE, INTERNAL_SERVER_ERROR, BAD_REQUEST, NO_CONTENT)
      .foreach { upstreamStatus =>
        s"must preserve upstream status $upstreamStatus without parsing the response body" in new TestSetup {
          respond(upstreamStatus, "not-json")

          val error = connector.getAgentZReference().failed.futureValue

          error mustBe a[UpstreamErrorResponse]
          error.asInstanceOf[UpstreamErrorResponse].statusCode mustBe upstreamStatus
          error.getMessage must include("Failed to retrieve disa-account agent preferences")
        }
      }

    Seq(
      new ConnectException("Connection refused"),
      new TimeoutException("Preferences request timed out"),
      UpstreamErrorResponse("Unauthenticated", UNAUTHORIZED),
      UpstreamErrorResponse("Unavailable", SERVICE_UNAVAILABLE)
    ).foreach { error =>
      s"must propagate an HTTP client failure unchanged: ${error.getMessage}" in new TestSetup {
        when(mockRequestBuilder.execute[HttpResponse](any(), any())).thenReturn(Future.failed(error))

        connector.getAgentZReference().failed.futureValue mustBe error
      }
    }
  }
}
