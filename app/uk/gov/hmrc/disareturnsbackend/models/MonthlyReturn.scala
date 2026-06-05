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
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import java.time.Instant

private object MonthlyReturnFormats {
  implicit val instantFormat: Format[Instant] =
    Format(MongoJavatimeFormats.instantReads, MongoJavatimeFormats.instantWrites)
}

final case class MonthlyReturn(
  zReference: String,
  taxYear: String,
  month: String,
  fileUploads: List[FileUpload],
  lastUpdated: Instant
) {

  def createFileUpload(reference: String, createdOn: Instant): MonthlyReturn =
    if (fileUploads.exists(_.reference == reference)) {
      this
    } else {
      copy(
        fileUploads = fileUploads :+ FileUpload(
          reference = reference,
          status = FileUploadStatus.Created,
          createdOn = createdOn
        ),
        lastUpdated = createdOn
      )
    }

  def completeFileUpload(
    reference: String,
    status: FileUploadStatus,
    completedOn: Instant,
    fileUploadDetails: Option[FileUploadDetails],
    downloadUrl: Option[String] = None,
    failureReason: Option[FileUploadFailureReason] = None,
    failureMessage: Option[String] = None
  ): MonthlyReturn = {
    val updatedUploads = fileUploads.map {
      case fileUpload if fileUpload.reference == reference =>
        fileUpload.copy(
          status = status,
          completedOn = Some(completedOn),
          fileUploadDetails = fileUploadDetails,
          downloadUrl = downloadUrl,
          failureReason = failureReason,
          failureMessage = failureMessage
        )
      case fileUpload                                      => fileUpload
    }

    if (updatedUploads == fileUploads) {
      this
    } else {
      copy(
        fileUploads = updatedUploads,
        lastUpdated = completedOn
      )
    }
  }
}

object MonthlyReturn {
  import MonthlyReturnFormats.instantFormat

  implicit val format: OFormat[MonthlyReturn] = Json.format[MonthlyReturn]
}

final case class FileUpload(
  reference: String,
  status: FileUploadStatus,
  createdOn: Instant,
  completedOn: Option[Instant] = None,
  fileUploadDetails: Option[FileUploadDetails] = None,
  downloadUrl: Option[String] = None,
  failureReason: Option[FileUploadFailureReason] = None,
  failureMessage: Option[String] = None
)

object FileUpload {
  import MonthlyReturnFormats.instantFormat

  implicit val format: OFormat[FileUpload] = Json.format[FileUpload]
}

final case class FileUploadDetails(
  fileName: String,
  fileMimeType: String,
  uploadTimestamp: Instant,
  checksum: String,
  size: Long
)

object FileUploadDetails {
  import MonthlyReturnFormats.instantFormat

  implicit val format: OFormat[FileUploadDetails] = Json.format[FileUploadDetails]
}

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

sealed trait FileUploadStatus {
  val value: String
}

object FileUploadStatus {

  case object Created extends FileUploadStatus { val value = "CREATED" }
  case object UpscanSuccess extends FileUploadStatus { val value = "UPSCAN_SUCCESS" }
  case object UpscanQuarantine extends FileUploadStatus { val value = "UPSCAN_QUARANTINE" }
  case object UpscanRejected extends FileUploadStatus { val value = "UPSCAN_REJECTED" }
  case object UpscanUnknown extends FileUploadStatus { val value = "UPSCAN_UNKNOWN" }

  val values: Seq[FileUploadStatus] =
    Seq(Created, UpscanSuccess, UpscanQuarantine, UpscanRejected, UpscanUnknown)

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
