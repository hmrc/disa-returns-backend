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

import base.SpecBase
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.http.Fault
import play.api.Application
import play.api.http.Status.*
import play.api.libs.json.{JsNull, Json}
import uk.gov.hmrc.http.{Authorization, HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.http.test.WireMockSupport

class DisaAccountConnectorISpec extends SpecBase with WireMockSupport {
  override lazy val app: Application = applicationBuilder()
    .configure(
      "microservice.services.disa-account.protocol" -> "http",
      "microservice.services.disa-account.host"     -> "localhost",
      "microservice.services.disa-account.port"     -> wireMockPort
    )
    .build()

  private val connector                  = inject[DisaAccountConnector]
  private val path                       = "/disa-account/agents/preferences"
  private implicit val hc: HeaderCarrier = HeaderCarrier(authorization = Some(Authorization(testBearerToken)))

  "DisaAccountConnector.getAgentZReference" - {
    Seq(testZReference, lowercaseTestZReference).foreach { reference =>
      s"must accept valid reference $reference and forward the user's bearer token" in {
        stubFor(get(urlEqualTo(path)).willReturn(okJson(Json.obj("zReference" -> reference).toString())))

        connector.getAgentZReference().futureValue mustBe reference

        verify(getRequestedFor(urlEqualTo(path)).withHeader("Authorization", equalTo(testBearerToken)))
      }
    }

    Seq(
      "missing"      -> Json.obj().toString(),
      "null"         -> Json.obj("zReference" -> JsNull).toString(),
      "non-string"   -> Json.obj("zReference" -> 1).toString(),
      "empty"        -> Json.obj("zReference" -> "").toString(),
      "blank"        -> Json.obj("zReference" -> "  ").toString(),
      "invalid"      -> Json.obj("zReference" -> invalidTestZReference).toString(),
      "padded"       -> Json.obj("zReference" -> s" $testZReference ").toString(),
      "invalid JSON" -> "not-json",
      "non-object"   -> Json.arr().toString()
    ).foreach { case (description, body) =>
      s"must fail for a $description successful response" in {
        stubFor(get(urlEqualTo(path)).willReturn(okJson(body)))

        connector.getAgentZReference().failed.futureValue mustBe a[Exception]
      }
    }

    Seq(UNAUTHORIZED, FORBIDDEN, NOT_FOUND, SERVICE_UNAVAILABLE, INTERNAL_SERVER_ERROR, BAD_REQUEST, NO_CONTENT)
      .foreach { upstreamStatus =>
        s"must preserve upstream status $upstreamStatus" in {
          stubFor(get(urlEqualTo(path)).willReturn(aResponse().withStatus(upstreamStatus)))

          val error = connector.getAgentZReference().failed.futureValue

          error mustBe a[UpstreamErrorResponse]
          error.asInstanceOf[UpstreamErrorResponse].statusCode mustBe upstreamStatus
        }
      }

    "must fail when the connection fails" in {
      stubFor(get(urlEqualTo(path)).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)))

      connector.getAgentZReference().failed.futureValue mustBe a[Exception]
    }
  }
}
