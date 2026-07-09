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
import play.api.mvc.{Result, Results}
import play.api.test.FakeRequest
import play.api.test.Helpers.*

import java.time.{Clock, Instant, ZoneOffset}
import scala.concurrent.Future

class ValidatedMonthlyReturnActionSpec extends SpecBase {

  "ValidatedMonthlyReturnAction" - {

    "must allow valid parameters when period checking is disabled" in {
      val result = runAction("2026-06-07T12:00:00Z", "2026-27", "6")

      status(result) mustBe OK
    }

    "must return BAD_REQUEST when path parameters are invalid" in {
      val result = runAction("2026-06-07T12:00:00Z", invalidTestTaxYear, testRouteMonth)

      status(result) mustBe BAD_REQUEST
      contentAsString(result) must include(taxYearFieldName)
    }

    "must allow the previous monthly period when period checking is enabled" in {
      val result = runAction("2026-06-07T12:00:00Z", "2026-27", "5", checkPeriod = true)

      status(result) mustBe OK
    }

    "must reject the current monthly period when period checking is enabled" in {
      val result = runAction("2026-06-07T12:00:00Z", "2026-27", "6", checkPeriod = true)

      status(result) mustBe UNPROCESSABLE_ENTITY
    }

    "must reject the previous month when the tax year does not match and period checking is enabled" in {
      val result = runAction("2026-06-07T12:00:00Z", "2025-26", "5", checkPeriod = true)

      status(result) mustBe UNPROCESSABLE_ENTITY
    }

    "must calculate April as the start of a tax year when period checking is enabled" in {
      val result = runAction("2026-05-07T12:00:00Z", "2026-27", "4", checkPeriod = true)

      status(result) mustBe OK
    }

    "must calculate March as the end of the previous tax year when period checking is enabled" in {
      val result = runAction("2026-04-07T12:00:00Z", "2025-26", "3", checkPeriod = true)

      status(result) mustBe OK
    }
  }

  private def runAction(
    now: String,
    taxYear: String,
    month: String,
    checkPeriod: Boolean = false
  ): Future[Result] = {
    val action = new ValidatedMonthlyReturnAction(
      stubControllerComponents(),
      Clock.fixed(Instant.parse(now), ZoneOffset.UTC)
    )

    action(testZReference, taxYear, month, checkPeriod).invokeBlock(
      FakeRequest(),
      _ => Future.successful(Results.Ok)
    )
  }
}
