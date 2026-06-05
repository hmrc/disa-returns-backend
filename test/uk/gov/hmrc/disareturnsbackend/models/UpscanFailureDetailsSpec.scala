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

package uk.gov.hmrc.disareturnsbackend.models

import play.api.libs.json.{JsValue, Json, OFormat}
import uk.gov.hmrc.disareturnsbackend.models.UpscanFailureReason.Quarantine
import uk.gov.hmrc.disareturnsbackend.utils.JsonFormatSpec

class UpscanFailureDetailsSpec extends JsonFormatSpec[UpscanFailureDetails] {

  override def model: UpscanFailureDetails =
    UpscanFailureDetails(
      failureReason = Quarantine,
      message = testEicarSignatureMessage
    )

  override def expectedJsonFromWrites: JsValue =
    Json.obj(
      failureReasonFieldName -> quarantineReasonString,
      messageFieldName       -> testEicarSignatureMessage
    )

  override implicit def format: OFormat[UpscanFailureDetails] = UpscanFailureDetails.format
}
