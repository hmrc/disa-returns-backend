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

import play.api.Application
import play.api.http.Status.{CREATED, NOT_FOUND, NO_CONTENT, OK}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import uk.gov.hmrc.disareturnsbackend.BaseIntegrationSpec
import uk.gov.hmrc.play.audit.http.connector.DatastreamMetrics

class TestOnlyOverridesControllerISpec extends BaseIntegrationSpec {

  override lazy val app: Application = new GuiceApplicationBuilder()
    .configure(config + ("application.router" -> "testOnlyDoNotUseInAppConf.Routes"))
    .overrides(bind[DatastreamMetrics].toInstance(DatastreamMetrics.disabled))
    .build()

  private val monthlyPath        = s"$testServicePath/monthly/$testZReference/$testTaxYear/$testMonth"
  private val overridesPath      = s"$testServicePath/test-only/overrides/$testZReference"
  private val monthlyReturnsPath = s"$testServicePath/test-only/monthly-returns"
  private val replacement        = Json.obj("clock" -> Json.obj("date" -> "2026-06-20"), "reportingWindow" -> None)

  "test-only override routes" should {
    "get submission's aggregate overrides" in {
      val result = get(s"$testServicePath/test-only/overrides/${testZReference.toLowerCase}")

      result.status shouldBe OK
      result.json shouldBe Json.obj("zReference" -> testZReference, "clock" -> None, "reportingWindow" -> None)
    }

    "replace overrides and use the clock date for monthly period checks" in {
      stubReturnsSubmissionTestOnlyOverrides("2026-06-20", overridden = true)

      putJson(overridesPath, replacement).status shouldBe OK
      postJson(monthlyPath, nilReturnFalseRequest).status shouldBe CREATED
    }

    "clear all overrides" in {
      delete(overridesPath).status shouldBe OK
    }

    "delete monthly returns" in {
      stubReturnsSubmissionTestOnlyOverrides("2026-06-20", overridden = true)

      putJson(overridesPath, replacement).status                shouldBe OK
      postJson(monthlyPath, nilReturnFalseRequest).status       shouldBe CREATED
      delete(monthlyReturnsPath).status                         shouldBe NO_CONTENT
      get(monthlyPath).status                                   shouldBe NOT_FOUND
    }
  }
}
