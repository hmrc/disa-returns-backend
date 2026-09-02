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

package uk.gov.hmrc.disareturnsbackend.testOnly.connectors

import base.SpecBase
import com.github.tomakehurst.wiremock.client.WireMock.*
import org.scalatest.BeforeAndAfterEach
import play.api.Application
import play.api.http.Status.{BAD_GATEWAY, OK}
import play.api.libs.json.Json
import uk.gov.hmrc.disareturnsbackend.testOnly.models.{ClockOverride, TestOverride, TestOverrideRequest}
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.http.test.WireMockSupport

import java.time.LocalDate

class TestOnlyReturnsSubmissionConnectorSpec extends SpecBase with WireMockSupport with BeforeAndAfterEach {
  override lazy val app: Application = applicationBuilder()
    .configure(
      "microservice.services.disa-returns-submission.protocol" -> "http",
      "microservice.services.disa-returns-submission.host"     -> "localhost",
      "microservice.services.disa-returns-submission.port"     -> wireMockPort
    )
    .build()

  private val connector = inject[TestOnlyReturnsSubmissionConnector]
  private implicit val hc: HeaderCarrier = HeaderCarrier()
  private val path = s"/disa-returns-submission/test-only/overrides/$testZReference"
  private val replacement = TestOverrideRequest(Some(ClockOverride(LocalDate.parse("2026-06-20"))), None)
  private val response    = TestOverride(testZReference, replacement.clock, replacement.reportingWindow)

  "TestOnlyReturnsSubmissionConnector" - {
    "must proxy aggregate GET, PUT and DELETE requests" in {
      Seq(get(path), put(path), delete(path)).foreach { mapping =>
        stubFor(
          mapping.willReturn(
            aResponse()
              .withStatus(OK)
              .withHeader("Content-Type", "application/json")
              .withBody(Json.toJson(response).toString())
          )
        )
      }

      connector.getOverrides(testZReference).futureValue mustBe response
      connector.setOverrides(testZReference, replacement).futureValue mustBe response
      connector.deleteOverrides(testZReference).futureValue mustBe response

      verify(putRequestedFor(urlEqualTo(path)).withRequestBody(equalToJson(Json.toJson(replacement).toString())))
    }

    "must fail for an unexpected submission response" in {
      stubFor(get(path).willReturn(aResponse().withStatus(BAD_GATEWAY)))

      connector.getOverrides(testZReference).failed.futureValue mustBe a[UpstreamErrorResponse]
    }
  }
}
