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

import play.api.libs.json.*

sealed trait FileUploadStatus {
  val value: String
}

object FileUploadStatus {

  case object Created extends FileUploadStatus { val value = "CREATED" }
  case object UpscanSuccess extends FileUploadStatus { val value = "UPSCAN_SUCCESS" }
  case object UpscanQuarantine extends FileUploadStatus { val value = "UPSCAN_QUARANTINE" }
  case object UpscanRejected extends FileUploadStatus { val value = "UPSCAN_REJECTED" }
  case object UpscanUnknown extends FileUploadStatus { val value = "UPSCAN_UNKNOWN" }
  case object Duplicate extends FileUploadStatus { val value = "DUPLICATE" }
  case object ValidationSuccess extends FileUploadStatus { val value = "VALIDATION_SUCCESS" }
  case object ValidationFailure extends FileUploadStatus { val value = "VALIDATION_FAILURE" }

  val values: Seq[FileUploadStatus] =
    Seq(
      Created,
      UpscanSuccess,
      UpscanQuarantine,
      UpscanRejected,
      UpscanUnknown,
      Duplicate,
      ValidationSuccess,
      ValidationFailure
    )

  private def fromString(value: String): Option[FileUploadStatus] =
    values.find(_.value == value)

  implicit val reads: Reads[FileUploadStatus] = Reads {
    case JsString(value) =>
      fromString(value)
        .map(JsSuccess(_))
        .getOrElse(JsError(s"Invalid file upload status: $value"))
    case _               =>
      JsError("File upload status must be a string")
  }

  implicit val writes: Writes[FileUploadStatus] =
    Writes(status => JsString(status.value))

  implicit val format: Format[FileUploadStatus] = Format(reads, writes)
}
