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

package uk.gov.hmrc.disareturnsbackend.testOnly.models

import play.api.libs.functional.syntax.*
import play.api.libs.json.{JsNull, JsPath, Json, JsonValidationError, OFormat, OWrites, Reads}

import java.time.{Instant, LocalDate}

final case class ClockOverride(date: LocalDate)

object ClockOverride {
  implicit val format: OFormat[ClockOverride] = Json.format[ClockOverride]
}

final case class ReportingWindowOverride(startDate: Instant, endDate: Instant)

object ReportingWindowOverride {
  implicit val format: OFormat[ReportingWindowOverride] = Json.format[ReportingWindowOverride]
}

final case class TestOverride(
  zReference: String,
  clock: Option[ClockOverride],
  reportingWindow: Option[ReportingWindowOverride]
)

object TestOverride {
  private val reads: Reads[TestOverride] = (
    (JsPath \ "zReference").read[String] and
      (JsPath \ "clock").readNullable[ClockOverride] and
      (JsPath \ "reportingWindow").readNullable[ReportingWindowOverride]
  )(TestOverride.apply)

  private val writes: OWrites[TestOverride] = OWrites { value =>
    Json.obj(
      "zReference"      -> value.zReference,
      "clock"           -> value.clock.map(Json.toJson(_)).getOrElse(JsNull),
      "reportingWindow" -> value.reportingWindow.map(Json.toJson(_)).getOrElse(JsNull)
    )
  }

  implicit val format: OFormat[TestOverride] = OFormat(reads, writes)
}

final case class TestOverrideRequest(
  clock: Option[ClockOverride],
  reportingWindow: Option[ReportingWindowOverride]
)

object TestOverrideRequest {
  private val reads: Reads[TestOverrideRequest] = (
    (JsPath \ "clock").readNullable[ClockOverride] and
      (JsPath \ "reportingWindow").readNullable[ReportingWindowOverride]
  )(TestOverrideRequest.apply)
    .filter(JsonValidationError("reportingWindow.startDate must be before or equal to reportingWindow.endDate")) {
      request =>
        request.reportingWindow.forall(window => !window.startDate.isAfter(window.endDate))
    }

  private val writes: OWrites[TestOverrideRequest] = OWrites { value =>
    Json.obj(
      "clock"           -> value.clock.map(Json.toJson(_)).getOrElse(JsNull),
      "reportingWindow" -> value.reportingWindow.map(Json.toJson(_)).getOrElse(JsNull)
    )
  }

  implicit val format: OFormat[TestOverrideRequest] = OFormat(reads, writes)
}
