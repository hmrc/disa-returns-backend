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
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.disareturnsbackend.testOnly.MutableClock

import java.time.LocalDate

class TestOnlyClockControllerSpec extends SpecBase {

  private val mutableClock = new MutableClock
  private val controller   = new TestOnlyClockController(stubControllerComponents(), mutableClock)

  "TestOnlyClockController" - {

    "must set the clock date" in {
      val result = controller.setDate("2026-05-20")(FakeRequest("PUT", "/test-only/clock/2026-05-20"))

      status(result) mustBe OK
      (contentAsJson(result) \ "date").as[String] mustBe "2026-05-20"
      LocalDate.now(mutableClock) mustBe LocalDate.parse("2026-05-20")
    }

    "must reject an invalid date" in {
      val result = controller.setDate("20-05-2026")(FakeRequest("PUT", "/test-only/clock/20-05-2026"))

      status(result) mustBe BAD_REQUEST
    }

    "must reset the clock" in {
      mutableClock.setDate(LocalDate.parse("2026-05-20"))

      val result = controller.resetClock(FakeRequest("DELETE", "/test-only/clock"))

      status(result) mustBe OK
      LocalDate.now(mutableClock) must not be LocalDate.parse("2026-05-20")
    }
  }
}
