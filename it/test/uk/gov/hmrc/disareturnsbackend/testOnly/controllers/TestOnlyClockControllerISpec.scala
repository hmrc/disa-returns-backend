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
import play.api.http.Status.{CREATED, NO_CONTENT, NOT_FOUND, OK, UNPROCESSABLE_ENTITY}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.disareturnsbackend.BaseIntegrationSpec
import uk.gov.hmrc.play.audit.http.connector.DatastreamMetrics

class TestOnlyClockControllerISpec extends BaseIntegrationSpec {

  override lazy val app: Application = new GuiceApplicationBuilder()
    .configure(config + ("application.router" -> "testOnlyDoNotUseInAppConf.Routes"))
    .overrides(bind[DatastreamMetrics].toInstance(DatastreamMetrics.disabled))
    .build()

  private val monthlyPath      = s"$testServicePath/monthly/$testZReference/$testTaxYear/$testMonth"
  private val declarationsPath = s"$monthlyPath/declarations"
  private val clockPath        = s"$testServicePath/test-only/clock"
  private val monthlyReturnsPath = s"$testServicePath/test-only/monthly-returns"

  "test-only clock routes" should {

    "set the app date used by declaration period checks" in {
      putString(s"$clockPath/2026-05-20").status shouldBe OK
      postJson(monthlyPath, nilReturnFalseRequest).status shouldBe CREATED

      val result = postJson(declarationsPath, emptyJson)

      result.status shouldBe UNPROCESSABLE_ENTITY
    }

    "reset the app clock" in {
      delete(clockPath).status shouldBe OK
    }

    "delete monthly returns" in {
      postJson(monthlyPath, nilReturnFalseRequest).status shouldBe CREATED

      delete(monthlyReturnsPath).status shouldBe NO_CONTENT

      get(monthlyPath).status shouldBe NOT_FOUND
    }
  }
}
