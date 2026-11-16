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

  def fileUploadsReadyForSubmission: List[FileUpload] =
    fileUploads.filter(upload =>
      upload.status == FileUploadStatus.ValidationSuccess &&
        upload.fileUploadDetails.exists(_.objectStoreFileLocation.isDefined)
    )

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

  def hasSuccessfulFileUploadMatchingChecksumAndNotMatchingReference(reference: String, checksum: String): Boolean =
    fileUploads.exists { fileUpload =>
      val hasSuccessfulStatus =
        fileUpload.status == FileUploadStatus.ValidationSuccess || fileUpload.status == FileUploadStatus.Submitted

      fileUpload.reference != reference && hasSuccessfulStatus && fileUpload.hasMatchingChecksum(checksum)
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
