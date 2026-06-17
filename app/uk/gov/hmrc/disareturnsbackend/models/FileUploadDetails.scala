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

import play.api.libs.json.{JsObject, JsString, JsValue, Json, OFormat, Reads}

import java.time.Instant

final case class FileUploadDetails(
  fileName: String,
  fileMimeType: String,
  uploadTimestamp: Instant,
  checksum: String,
  size: Long,
  upscanDownloadUrl: String,
  upscanCompletedOn: Option[Instant] = None,
  objectStoreFileLocation: Option[String] = None,
  objectStoreFileErrorsLocation: Option[String] = None,
  validation: Option[FileUploadValidationResult] = None
)

object FileUploadDetails {
  private val derivedFormat: OFormat[FileUploadDetails] = Json.using[Json.WithDefaultValues].format[FileUploadDetails]

  private def legacyEmptyStringsAsNull(json: JsValue): JsValue =
    json match {
      case jsonObject: JsObject =>
        Seq("objectStoreFileLocation", "objectStoreFileErrorsLocation").foldLeft(jsonObject) { case (updated, field) =>
          (updated \ field).toOption match {
            case Some(JsString(value)) if value.trim.isEmpty => updated - field
            case _                                           => updated
          }
        }
      case other                => other
    }

  private def normalise(details: FileUploadDetails): FileUploadDetails =
    details.copy(
      objectStoreFileLocation = details.objectStoreFileLocation.filter(_.trim.nonEmpty),
      objectStoreFileErrorsLocation = details.objectStoreFileErrorsLocation.filter(_.trim.nonEmpty)
    )

  implicit val format: OFormat[FileUploadDetails] =
    OFormat(
      Reads(json => derivedFormat.reads(legacyEmptyStringsAsNull(json)).map(normalise)),
      derivedFormat
    )

  val mongoFormat: OFormat[FileUploadDetails] = {
    import MonthlyReturnFormats.mongoInstantFormat

    implicit val validationResultFormat: OFormat[FileUploadValidationResult] = FileUploadValidationResult.format

    val derivedMongoFormat: OFormat[FileUploadDetails] = Json.using[Json.WithDefaultValues].format[FileUploadDetails]

    OFormat(
      Reads(json => derivedMongoFormat.reads(legacyEmptyStringsAsNull(json)).map(normalise)),
      derivedMongoFormat
    )
  }
}
