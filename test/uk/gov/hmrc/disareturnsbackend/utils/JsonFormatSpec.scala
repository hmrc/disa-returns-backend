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

package uk.gov.hmrc.disareturnsbackend.utils

import base.SpecBase
import play.api.libs.json.{Format, JsValue, Json}

trait JsonFormatSpec[A] extends SpecBase {

  def model: A
  def expectedJsonFromWrites: JsValue
  def incomingJsonToRead: JsValue = expectedJsonFromWrites
  implicit def format: Format[A]

  "must serialise to JSON" in {
    Json.toJson(model) mustBe expectedJsonFromWrites
  }

  "must deserialise from JSON" in {
    incomingJsonToRead.as[A] mustBe model
  }
}
