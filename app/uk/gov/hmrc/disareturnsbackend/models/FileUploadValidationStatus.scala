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

sealed trait FileUploadValidationStatus {
  val value: String
}

object FileUploadValidationStatus {

  case object ValidationSuccess extends FileUploadValidationStatus { val value = "ValidationSuccess" }
  case object ValidationFailed extends FileUploadValidationStatus { val value = "ValidationFailed" }
  case object InvalidFile extends FileUploadValidationStatus { val value = "InvalidFile" }

  val values: Seq[FileUploadValidationStatus] =
    Seq(ValidationSuccess, ValidationFailed, InvalidFile)

  private def fromString(value: String): Option[FileUploadValidationStatus] =
    values.find(_.value == value)

  implicit val reads: Reads[FileUploadValidationStatus] = Reads {
    case JsString(value) =>
      fromString(value)
        .map(JsSuccess(_))
        .getOrElse(JsError(s"Invalid file upload validation status: $value"))
    case _               =>
      JsError("File upload validation status must be a string")
  }

  implicit val writes: Writes[FileUploadValidationStatus] =
    Writes(status => JsString(status.value))

  implicit val format: Format[FileUploadValidationStatus] = Format(reads, writes)
}
