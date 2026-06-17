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

sealed trait FileUploadFailureReason {
  val value: String
}

object FileUploadFailureReason {

  case object Quarantine extends FileUploadFailureReason { val value = "QUARANTINE" }
  case object Rejected extends FileUploadFailureReason { val value = "REJECTED" }
  case object Unknown extends FileUploadFailureReason { val value = "UNKNOWN" }

  val values: Seq[FileUploadFailureReason] =
    Seq(Quarantine, Rejected, Unknown)

  private def fromString(value: String): Option[FileUploadFailureReason] =
    values.find(_.value == value)

  implicit val reads: Reads[FileUploadFailureReason] = Reads {
    case JsString(value) =>
      fromString(value)
        .map(JsSuccess(_))
        .getOrElse(JsError(s"Invalid file upload failure reason: $value"))
    case _               =>
      JsError("File upload failure reason must be a string")
  }

  implicit val writes: Writes[FileUploadFailureReason] =
    Writes(reason => JsString(reason.value))

  implicit val format: Format[FileUploadFailureReason] = Format(reads, writes)
}
