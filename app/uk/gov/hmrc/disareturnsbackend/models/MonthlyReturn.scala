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
import java.util.UUID

private object MonthlyReturnFormats {
  implicit val mongoInstantFormat: Format[Instant] =
    Format(MongoJavatimeFormats.instantReads, MongoJavatimeFormats.instantWrites)

  def withCreatedOnDefault(json: JsValue): JsValue =
    json match {
      case jsonObject: JsObject if (jsonObject \ "createdOn").isEmpty =>
        (jsonObject \ "lastUpdated").toOption.fold(jsonObject)(lastUpdated => jsonObject + ("createdOn" -> lastUpdated))
      case _                                                          => json
    }
}

final case class MonthlyReturn(
  zReference: String,
  submissionId: UUID,
  taxYear: String,
  month: Int,
  createdOn: Instant,
  nilReturn: Boolean = false,
  fileUploads: List[FileUpload],
  declaredOn: Option[Instant] = None,
  lastUpdated: Instant
) {

  def hasDeclaration: Boolean = declaredOn.isDefined

  def declare(declaredOn: Instant): MonthlyReturn =
    if (hasDeclaration) {
      this
    } else {
      copy(
        declaredOn = Some(declaredOn),
        lastUpdated = declaredOn
      )
    }

  def createFileUpload(reference: String, createdOn: Instant): MonthlyReturn = {
    val cannotAcceptFileUpload = nilReturn || hasDeclaration || fileUploads.exists(_.reference == reference)

    if (cannotAcceptFileUpload) {
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
  }

  def deleteFileUpload(reference: String, updatedOn: Instant): MonthlyReturn = {
    val updatedUploads = fileUploads.filterNot(_.reference == reference)

    if (updatedUploads == fileUploads) {
      this
    } else {
      copy(
        fileUploads = updatedUploads,
        lastUpdated = updatedOn
      )
    }
  }

  def completeUpscan(
    reference: String,
    status: FileUploadStatus,
    upscanCompletedOn: Instant,
    fileUploadDetails: Option[FileUploadDetails],
    failureReason: Option[FileUploadFailureReason] = None,
    failureMessage: Option[String] = None
  ): MonthlyReturn =
    if (nilReturn) {
      this
    } else {
      val completedFileUpload = FileUpload(
        reference = reference,
        status = status,
        createdOn = upscanCompletedOn,
        fileUploadDetails = fileUploadDetails.map(_.copy(upscanCompletedOn = Some(upscanCompletedOn))),
        failureReason = failureReason,
        failureMessage = failureMessage
      )

      val updatedUploads =
        if (fileUploads.exists(_.reference == reference)) {
          fileUploads.map {
            case fileUpload if fileUpload.reference == reference =>
              completedFileUpload.copy(createdOn = fileUpload.createdOn)
            case fileUpload                                      => fileUpload
          }
        } else {
          fileUploads :+ completedFileUpload
        }

      if (updatedUploads == fileUploads) {
        this
      } else {
        copy(
          fileUploads = updatedUploads,
          lastUpdated = upscanCompletedOn
        )
      }
    }

  def updateNilReturn(nilReturn: Boolean, updatedOn: Instant): MonthlyReturn =
    if (nilReturn) {
      val updatedReturn = copy(
        nilReturn = true,
        fileUploads = Nil,
        lastUpdated = updatedOn
      )

      if (updatedReturn == this) this else updatedReturn
    } else if (this.nilReturn) {
      copy(
        nilReturn = false,
        lastUpdated = updatedOn
      )
    } else {
      this
    }
}

object MonthlyReturn {
  import MonthlyReturnFormats.withCreatedOnDefault

  implicit val uuidFormat: Format[UUID] = UuidFormat.format

  private val derivedFormat: OFormat[MonthlyReturn] =
    Json.using[Json.WithDefaultValues].format[MonthlyReturn]

  implicit val format: OFormat[MonthlyReturn] = OFormat(
    Reads(json => derivedFormat.reads(withCreatedOnDefault(json))),
    derivedFormat
  )

  val mongoFormat: OFormat[MonthlyReturn] = {
    import MonthlyReturnFormats.mongoInstantFormat

    implicit val fileUploadFormat: OFormat[FileUpload] = FileUpload.mongoFormat

    val derivedMongoFormat: OFormat[MonthlyReturn] =
      Json.using[Json.WithDefaultValues].format[MonthlyReturn]

    OFormat(
      Reads(json => derivedMongoFormat.reads(withCreatedOnDefault(json))),
      derivedMongoFormat
    )
  }
}

final case class FileUpload(
  reference: String,
  status: FileUploadStatus,
  createdOn: Instant,
  fileUploadDetails: Option[FileUploadDetails] = None,
  failureReason: Option[FileUploadFailureReason] = None,
  failureMessage: Option[String] = None
)

object FileUpload {
  implicit val format: OFormat[FileUpload] = Json.format[FileUpload]

  val mongoFormat: OFormat[FileUpload] = {
    import MonthlyReturnFormats.mongoInstantFormat

    implicit val fileUploadDetailsFormat: OFormat[FileUploadDetails] = FileUploadDetails.mongoFormat

    Json.format[FileUpload]
  }
}

final case class FileUploadDetails(
  fileName: String,
  fileMimeType: String,
  uploadTimestamp: Instant,
  checksum: String,
  size: Long,
  upscanDownloadUrl: String,
  upscanCompletedOn: Option[Instant] = None
)

object FileUploadDetails {
  implicit val format: OFormat[FileUploadDetails] = Json.format[FileUploadDetails]

  val mongoFormat: OFormat[FileUploadDetails] = {
    import MonthlyReturnFormats.mongoInstantFormat

    Json.format[FileUploadDetails]
  }
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
