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
import play.api.libs.json.Json.toJsObject

import java.time.Instant

sealed trait UpscanResult {
  def reference: String
}

final case class UpscanSuccess(
  reference: String,
  downloadUrl: String,
  uploadDetails: UpscanDetails
) extends UpscanResult

object UpscanSuccess {
  implicit val format: OFormat[UpscanSuccess] = Json.format[UpscanSuccess]
}

final case class UpscanFailure(
  reference: String,
  failureDetails: UpscanFailureDetails
) extends UpscanResult

object UpscanFailure {
  implicit val format: OFormat[UpscanFailure] = Json.format[UpscanFailure]
}

final case class UpscanDetails(
  fileName: String,
  fileMimeType: String,
  uploadTimestamp: Instant,
  checksum: String,
  size: Long
)

object UpscanDetails {
  implicit val format: OFormat[UpscanDetails] = Json.format[UpscanDetails]
}

final case class UpscanFailureDetails(
  failureReason: UpscanFailureReason,
  message: String
)

object UpscanFailureDetails {
  implicit val format: OFormat[UpscanFailureDetails] = Json.format[UpscanFailureDetails]
}

sealed trait UpscanFailureReason {
  val value: String
}

object UpscanFailureReason {

  case object Quarantine extends UpscanFailureReason { val value = "QUARANTINE" }
  case object Rejected extends UpscanFailureReason { val value = "REJECTED" }
  case object Unknown extends UpscanFailureReason { val value = "UNKNOWN" }

  val values: Seq[UpscanFailureReason] =
    Seq(Quarantine, Rejected, Unknown)

  private def fromString(value: String): Option[UpscanFailureReason] =
    values.find(_.value == value)

  implicit val reads: Reads[UpscanFailureReason] = Reads {
    case JsString(value) =>
      fromString(value)
        .map(JsSuccess(_))
        .getOrElse(JsError(s"Invalid upscan failure reason: $value"))
    case _               =>
      JsError("Upscan failure reason must be a string")
  }

  implicit val writes: Writes[UpscanFailureReason] =
    Writes(reason => JsString(reason.value))

  implicit val format: Format[UpscanFailureReason] = Format(reads, writes)
}

object UpscanResult {

  implicit val reads: Reads[UpscanResult] =
    (__ \ "fileStatus").read[String].flatMap {
      case "READY"  => __.read[UpscanSuccess].map(success => success: UpscanResult)
      case "FAILED" => __.read[UpscanFailure].map(failure => failure: UpscanResult)
      case _        => Reads.failed("Invalid upscan fileStatus")
    }

  implicit val writes: OWrites[UpscanResult] = OWrites {
    case success: UpscanSuccess =>
      toJsObject(success) ++ Json.obj("fileStatus" -> "READY")
    case failure: UpscanFailure =>
      toJsObject(failure) ++ Json.obj("fileStatus" -> "FAILED")
  }
}
