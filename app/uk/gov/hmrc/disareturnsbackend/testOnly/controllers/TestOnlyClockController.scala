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

import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.disareturnsbackend.testOnly.MutableClock
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import java.time.{DateTimeException, LocalDate}
import javax.inject.{Inject, Singleton}

@Singleton
class TestOnlyClockController @Inject() (
  cc: ControllerComponents,
  mutableClock: MutableClock
) extends BackendController(cc) {

  def getClock: Action[AnyContent] = Action {
    Ok(clockJson)
  }

  def setDate(date: String): Action[AnyContent] = Action {
    try {
      mutableClock.setDate(LocalDate.parse(date))
      Ok(clockJson)
    } catch {
      case _: DateTimeException => BadRequest(Json.obj("message" -> "date must be in yyyy-MM-dd format"))
    }
  }

  def resetClock: Action[AnyContent] = Action {
    mutableClock.reset()
    Ok(clockJson)
  }

  private def clockJson =
    Json.obj(
      "date"    -> LocalDate.now(mutableClock).toString,
      "instant" -> mutableClock.instant().toString
    )
}
