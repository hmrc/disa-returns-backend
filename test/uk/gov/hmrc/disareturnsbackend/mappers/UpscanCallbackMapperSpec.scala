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

import base.SpecBase
import uk.gov.hmrc.disareturnsbackend.models.*

class UpscanCallbackMapperSpec extends SpecBase {

  private val mapper: UpscanCallbackMapper = new UpscanCallbackMapperImpl()

  "toFileUploadDetails" - {

    "must map UpscanDetails to FileUploadDetails" in {
      val uploadDetails = UpscanDetails(
        fileName = testFileName,
        fileMimeType = testFileMimeType,
        uploadTimestamp = testCreatedOn,
        checksum = testChecksum,
        size = testFileSize
      )

      mapper.toFileUploadDetails(uploadDetails, testDownloadUrl) mustBe FileUploadDetails(
        fileName = uploadDetails.fileName,
        fileMimeType = uploadDetails.fileMimeType,
        uploadTimestamp = uploadDetails.uploadTimestamp,
        checksum = uploadDetails.checksum,
        size = uploadDetails.size,
        upscanDownloadUrl = testDownloadUrl
      )
    }
  }

  "toFileUploadStatus" -
    Seq(
      UpscanFailureReason.Quarantine -> FileUploadStatus.UpscanQuarantine,
      UpscanFailureReason.Rejected   -> FileUploadStatus.UpscanRejected,
      UpscanFailureReason.Unknown    -> FileUploadStatus.UpscanUnknown
    ).foreach { case (upscanFailureReason, fileUploadStatus) =>
      s"must map ${upscanFailureReason.value} to ${fileUploadStatus.value}" in {
        mapper.toFileUploadStatus(upscanFailureReason) mustBe fileUploadStatus
      }
    }

  "toFileUploadFailureReason" -
    Seq(
      UpscanFailureReason.Quarantine -> FileUploadFailureReason.Quarantine,
      UpscanFailureReason.Rejected   -> FileUploadFailureReason.Rejected,
      UpscanFailureReason.Unknown    -> FileUploadFailureReason.Unknown
    ).foreach { case (upscanFailureReason, fileUploadFailureReason) =>
      s"must map ${upscanFailureReason.value} to ${fileUploadFailureReason.value}" in {
        mapper.toFileUploadFailureReason(upscanFailureReason) mustBe fileUploadFailureReason
      }
    }
}
