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

package uk.gov.hmrc.disareturnsbackend.testOnly.controllers

import base.SpecBase
import org.apache.pekko.stream.Materializer
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.{verify, when}
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.disareturnsbackend.testOnly.connectors.TestOnlyReturnsSubmissionConnector
import uk.gov.hmrc.disareturnsbackend.testOnly.models.{ClockOverride, ReportingWindowOverride, TestOverride, TestOverrideRequest}

import java.time.{Instant, LocalDate}
import scala.concurrent.Future

class TestOnlyOverridesControllerSpec extends SpecBase {
  private implicit lazy val materializer: Materializer = app.materializer
  private val connector                                = mock[TestOnlyReturnsSubmissionConnector]
  private val controller                               = new TestOnlyOverridesController(stubControllerComponents(), connector)
  private val replacement                              = TestOverrideRequest(
    Some(ClockOverride(LocalDate.parse("2026-06-20"))),
    Some(
      ReportingWindowOverride(
        Instant.parse("2026-06-19T23:59:00Z"),
        Instant.parse("2026-06-20T00:01:00Z")
      )
    )
  )
  private val response                                 = TestOverride(testZReference, replacement.clock, replacement.reportingWindow)

  "TestOnlyOverridesController" - {
    "must get overrides for the normalized Z-reference" in {
      when(connector.getOverrides(eqTo(testZReference))(any())).thenReturn(Future.successful(response))

      val result = controller.get(lowercaseTestZReference)(FakeRequest())

      status(result) mustBe OK
      contentAsJson(result) mustBe Json.toJson(response)
      verify(connector).getOverrides(eqTo(testZReference))(any())
    }

    "must forward a full replacement document" in {
      when(connector.setOverrides(eqTo(testZReference), eqTo(replacement))(any()))
        .thenReturn(Future.successful(response))

      val result = controller.set(lowercaseTestZReference)(
        FakeRequest(PUT, "/test-only/overrides").withBody(replacement)
      )

      status(result) mustBe OK
      contentAsJson(result) mustBe Json.toJson(response)
      verify(connector).setOverrides(eqTo(testZReference), eqTo(replacement))(any())
    }

    "must clear all overrides" in {
      val cleared = TestOverride(testZReference, None, None)
      when(connector.deleteOverrides(eqTo(testZReference))(any())).thenReturn(Future.successful(cleared))

      val result = controller.delete(testZReference)(FakeRequest())

      status(result) mustBe OK
      contentAsJson(result) mustBe Json.toJson(cleared)
      verify(connector).deleteOverrides(eqTo(testZReference))(any())
    }

    "must reject an invalid Z-reference" in {
      status(controller.get("invalid")(FakeRequest())) mustBe BAD_REQUEST
    }

    "must reject an invalid replacement document" in {
      val invalidBody = Json.obj("clock" -> Json.obj("date" -> "20-06-2026"), "reportingWindow" -> None)

      val result =
        call(controller.set(testZReference), FakeRequest(PUT, "/test-only/overrides").withJsonBody(invalidBody))

      status(result) mustBe BAD_REQUEST
    }

    "must reject an inverted reporting window" in {
      val invalidBody = Json.obj(
        "clock"           -> None,
        "reportingWindow" -> Json.obj(
          "startDate" -> "2026-06-20T00:01:00Z",
          "endDate"   -> "2026-06-19T23:59:00Z"
        )
      )

      val result =
        call(controller.set(testZReference), FakeRequest(PUT, "/test-only/overrides").withJsonBody(invalidBody))

      status(result) mustBe BAD_REQUEST
    }

    Seq("get", "set", "delete").foreach { operation =>
      s"must return ServiceUnavailable when submission fails to $operation overrides" in {
        operation match {
          case "get"    =>
            when(connector.getOverrides(eqTo(testZReference))(any())).thenReturn(Future.failed(new RuntimeException))
          case "set"    =>
            when(connector.setOverrides(eqTo(testZReference), any[TestOverrideRequest]())(any()))
              .thenReturn(Future.failed(new RuntimeException))
          case "delete" =>
            when(connector.deleteOverrides(eqTo(testZReference))(any())).thenReturn(Future.failed(new RuntimeException))
        }

        val result = operation match {
          case "get"    => controller.get(testZReference)(FakeRequest())
          case "set"    =>
            controller.set(testZReference)(
              FakeRequest(PUT, "/test-only/overrides").withBody(replacement)
            )
          case "delete" => controller.delete(testZReference)(FakeRequest())
        }

        status(result) mustBe SERVICE_UNAVAILABLE
      }
    }
  }
}
