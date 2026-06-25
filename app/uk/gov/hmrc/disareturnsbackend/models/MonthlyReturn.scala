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
  lastUpdated: Instant
) {

  def getFileUpload(reference: String): Option[FileUpload] =
    fileUploads.find(_.reference == reference)

  def createFileUpload(reference: String, createdOn: Instant): MonthlyReturn = {
    val cannotAcceptFileUpload = nilReturn || fileUploads.exists(_.reference == reference)

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
    if (!canCompleteUpscan(reference)) {
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
        fileUploads.map {
          case fileUpload if fileUpload.reference == reference =>
            completedFileUpload.copy(createdOn = fileUpload.createdOn)
          case fileUpload                                      => fileUpload
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

  def canCompleteUpscan(reference: String): Boolean =
    if (nilReturn) {
      false
    } else {
      fileUploads.exists { fileUpload =>
        val hasMatchingReference = fileUpload.reference == reference
        val hasCreatedStatus     = fileUpload.status == FileUploadStatus.Created

        hasMatchingReference && hasCreatedStatus
      }
    }

  def hasFileUploadMatchingChecksumAndNotMatchingReference(reference: String, checksum: String): Boolean =
    fileUploads.exists { fileUpload =>
      fileUpload.reference != reference && fileUpload.hasMatchingChecksum(checksum)
    }

  def markUpscanExpired(reference: String, updatedOn: Instant): MonthlyReturn = {
    val updatedUploads = fileUploads.map {
      case fileUpload if fileUpload.reference == reference && fileUpload.status == FileUploadStatus.UpscanSuccess =>
        fileUpload.copy(status = FileUploadStatus.UpscanExpired)
      case fileUpload                                                                                             => fileUpload
    }

    if (updatedUploads == fileUploads) {
      this
    } else {
      copy(
        fileUploads = updatedUploads,
        lastUpdated = updatedOn
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

  def updateFileUploadProcessingDetails(
    reference: String,
    validation: FileUploadValidationResult,
    objectStoreFileLocation: Option[String],
    objectStoreFileErrorsLocation: Option[String],
    updatedOn: Instant
  ): MonthlyReturn = {
    val updatedUploads = fileUploads.map {
      case fileUpload if fileUpload.reference == reference =>
        val validationStatus = validation.status match {
          case FileUploadValidationStatus.ValidationSuccess => FileUploadStatus.ValidationSuccess
          case FileUploadValidationStatus.ValidationFailed  => FileUploadStatus.ValidationFailure
          case FileUploadValidationStatus.InvalidFile       => FileUploadStatus.ValidationFailure
        }
        fileUpload.fileUploadDetails match {
          case Some(details) =>
            val updatedDetails = details.copy(
              objectStoreFileLocation = objectStoreFileLocation,
              objectStoreFileErrorsLocation = objectStoreFileErrorsLocation,
              validation = Some(validation)
            )

            fileUpload.copy(status = validationStatus, fileUploadDetails = Some(updatedDetails))

          case None =>
            fileUpload
        }

      case fileUpload =>
        fileUpload
    }

    if (updatedUploads == fileUploads) {
      this
    } else {
      copy(
        fileUploads = updatedUploads,
        lastUpdated = updatedOn
      )
    }
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
