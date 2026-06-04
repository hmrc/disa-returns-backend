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

package uk.gov.hmrc.disareturnsbackend.models.upscan

import play.api.libs.json.*
import play.api.libs.json.Json.toJsObject

import java.time.Instant

sealed trait UpscanUploadResult {
  def reference: String
}

final case class UpscanUploadSuccess(
  reference: String,
  downloadUrl: String,
  uploadDetails: UpscanUploadDetails
) extends UpscanUploadResult

object UpscanUploadSuccess {
  implicit val format: OFormat[UpscanUploadSuccess] = Json.format[UpscanUploadSuccess]
}

final case class UpscanUploadFailure(
  reference: String,
  failureDetails: UpscanUploadFailureDetails
) extends UpscanUploadResult

object UpscanUploadFailure {
  implicit val format: OFormat[UpscanUploadFailure] = Json.format[UpscanUploadFailure]
}

final case class UpscanUploadDetails(
  fileName: String,
  fileMimeType: String,
  uploadTimestamp: Instant,
  checksum: String,
  size: Long
)

object UpscanUploadDetails {
  implicit val format: OFormat[UpscanUploadDetails] = Json.format[UpscanUploadDetails]
}

final case class UpscanUploadFailureDetails(
  failureReason: UpscanUploadFailureReason,
  message: String
)

object UpscanUploadFailureDetails {
  implicit val format: OFormat[UpscanUploadFailureDetails] = Json.format[UpscanUploadFailureDetails]
}

sealed trait UpscanUploadFailureReason {
  val value: String
}

object UpscanUploadFailureReason {

  case object Quarantine extends UpscanUploadFailureReason { val value = "QUARANTINE" }
  case object Rejected extends UpscanUploadFailureReason { val value = "REJECTED" }
  case object Unknown extends UpscanUploadFailureReason { val value = "UNKNOWN" }

  val values: Seq[UpscanUploadFailureReason] =
    Seq(Quarantine, Rejected, Unknown)

  private def fromString(value: String): Option[UpscanUploadFailureReason] =
    values.find(_.value == value)

  implicit val reads: Reads[UpscanUploadFailureReason] = Reads {
    case JsString(value) =>
      fromString(value)
        .map(JsSuccess(_))
        .getOrElse(JsError(s"Invalid upscan upload failure reason: $value"))
    case _               =>
      JsError("Upscan upload failure reason must be a string")
  }

  implicit val writes: Writes[UpscanUploadFailureReason] =
    Writes(reason => JsString(reason.value))

  implicit val format: Format[UpscanUploadFailureReason] = Format(reads, writes)
}

object UpscanUploadResult {

  implicit val reads: Reads[UpscanUploadResult] =
    (__ \ "fileStatus").read[String].flatMap {
      case "READY"  => __.read[UpscanUploadSuccess].map(success => success: UpscanUploadResult)
      case "FAILED" => __.read[UpscanUploadFailure].map(failure => failure: UpscanUploadResult)
      case _        => Reads.failed("Invalid upscan upload fileStatus")
    }

  implicit val writes: OWrites[UpscanUploadResult] = OWrites {
    case success: UpscanUploadSuccess =>
      toJsObject(success) ++ Json.obj("fileStatus" -> "READY")
    case failure: UpscanUploadFailure =>
      toJsObject(failure) ++ Json.obj("fileStatus" -> "FAILED")
  }
}
