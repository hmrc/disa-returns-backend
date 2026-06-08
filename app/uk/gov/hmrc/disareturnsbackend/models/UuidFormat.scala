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

import play.api.libs.json.{Format, JsError, JsString, JsSuccess, Reads, Writes}

import java.util.UUID
import scala.util.Try

object UuidFormat {

  implicit val format: Format[UUID] =
    Format(
      Reads {
        case JsString(value) =>
          Try(UUID.fromString(value)).fold(_ => JsError("error.expected.uuid"), JsSuccess(_))
        case _               =>
          JsError("error.expected.uuid")
      },
      Writes(uuid => JsString(uuid.toString))
    )
}
