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

package uk.gov.hmrc.disareturnsbackend.mappers

import uk.gov.hmrc.disareturnsbackend.models.*

import javax.inject.{Inject, Singleton}

@Singleton
class UpscanCallbackMapperImpl @Inject() () extends UpscanCallbackMapper {

  override def toFileUploadDetails(uploadDetails: UpscanDetails): FileUploadDetails =
    FileUploadDetails(
      fileName = uploadDetails.fileName,
      fileMimeType = uploadDetails.fileMimeType,
      uploadTimestamp = uploadDetails.uploadTimestamp,
      checksum = uploadDetails.checksum,
      size = uploadDetails.size
    )

  override def toFileUploadStatus(failureReason: UpscanFailureReason): FileUploadStatus =
    failureReason match {
      case UpscanFailureReason.Quarantine => FileUploadStatus.UpscanQuarantine
      case UpscanFailureReason.Rejected   => FileUploadStatus.UpscanRejected
      case UpscanFailureReason.Unknown    => FileUploadStatus.UpscanUnknown
    }

  override def toFileUploadFailureReason(failureReason: UpscanFailureReason): FileUploadFailureReason =
    failureReason match {
      case UpscanFailureReason.Quarantine => FileUploadFailureReason.Quarantine
      case UpscanFailureReason.Rejected   => FileUploadFailureReason.Rejected
      case UpscanFailureReason.Unknown    => FileUploadFailureReason.Unknown
    }
}
