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

package uk.gov.hmrc.disareturnsbackend.services

import base.SpecBase
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.{reset, verify, when}
import org.scalatest.BeforeAndAfterEach
import uk.gov.hmrc.disareturnsbackend.mappers.{UpscanCallbackMapper, UpscanCallbackMapperImpl}
import uk.gov.hmrc.disareturnsbackend.models.*
import uk.gov.hmrc.disareturnsbackend.repositories.MonthlyReturnRepository

import java.time.Instant
import scala.concurrent.Future

class UpscanCallbackServiceSpec extends SpecBase with BeforeAndAfterEach {

  private val mockMonthlyReturnRepository                = mock[MonthlyReturnRepository]
  private val upscanCallbackMapper: UpscanCallbackMapper = new UpscanCallbackMapperImpl()
  private val service                                    = new UpscanCallbackService(mockMonthlyReturnRepository, upscanCallbackMapper)

  private val zReference                = "Z1234"
  private val taxYear                   = "2026"
  private val month                     = "5"
  private val upscanReference           = "2b4d6f3a-8c1e-4e4b-9c7a-123456789abc"
  private val downloadUrl               = "https://fus-outbound-bucket.s3.eu-west-2.amazonaws.com/object-key?X-Amz-Signature=abc"
  private val uploadDetails             = UpscanDetails(
    fileName = "return.csv",
    fileMimeType = "text/csv",
    uploadTimestamp = Instant.parse("2026-05-17T12:00:00Z"),
    checksum = "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
    size = 1024L
  )
  private val expectedFileUploadDetails = FileUploadDetails(
    fileName = uploadDetails.fileName,
    fileMimeType = uploadDetails.fileMimeType,
    uploadTimestamp = uploadDetails.uploadTimestamp,
    checksum = uploadDetails.checksum,
    size = uploadDetails.size
  )

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockMonthlyReturnRepository)
  }

  "monthlyReturnUpscanCallback" - {

    "must complete a file upload as UPSCAN_SUCCESS for a READY callback" in {
      stubCompleteFileUpload(result = true)

      service
        .monthlyReturnUpscanCallback(
          zReference,
          taxYear,
          month,
          UpscanSuccess(
            reference = upscanReference,
            downloadUrl = downloadUrl,
            uploadDetails = uploadDetails
          )
        )
        .futureValue

      verify(mockMonthlyReturnRepository).completeFileUpload(
        eqTo(zReference),
        eqTo(taxYear),
        eqTo(month),
        eqTo(upscanReference),
        eqTo(FileUploadStatus.UpscanSuccess),
        eqTo(Some(expectedFileUploadDetails)),
        eqTo(Some(downloadUrl)),
        eqTo(Option.empty[FileUploadFailureReason]),
        eqTo(Option.empty[String])
      )
    }

    Seq(
      UpscanFailureReason.Quarantine -> (FileUploadStatus.UpscanQuarantine, FileUploadFailureReason.Quarantine),
      UpscanFailureReason.Rejected   -> (FileUploadStatus.UpscanRejected, FileUploadFailureReason.Rejected),
      UpscanFailureReason.Unknown    -> (FileUploadStatus.UpscanUnknown, FileUploadFailureReason.Unknown)
    ).foreach { case (upscanFailureReason, (fileUploadStatus, fileUploadFailureReason)) =>
      s"must complete a file upload as ${fileUploadStatus.value} for a ${upscanFailureReason.value} callback" in {
        stubCompleteFileUpload(result = false)

        service
          .monthlyReturnUpscanCallback(
            zReference,
            taxYear,
            month,
            UpscanFailure(
              reference = upscanReference,
              failureDetails = UpscanFailureDetails(
                failureReason = upscanFailureReason,
                message = "Upscan failure message"
              )
            )
          )
          .futureValue

        verify(mockMonthlyReturnRepository).completeFileUpload(
          eqTo(zReference),
          eqTo(taxYear),
          eqTo(month),
          eqTo(upscanReference),
          eqTo(fileUploadStatus),
          eqTo(Option.empty[FileUploadDetails]),
          eqTo(Option.empty[String]),
          eqTo(Some(fileUploadFailureReason)),
          eqTo(Some("Upscan failure message"))
        )
      }
    }

    "must fail when the repository fails for a READY callback" in {
      stubCompleteFileUpload(Future.failed(new RuntimeException("mongodb down")))

      service
        .monthlyReturnUpscanCallback(
          zReference,
          taxYear,
          month,
          UpscanSuccess(
            reference = upscanReference,
            downloadUrl = downloadUrl,
            uploadDetails = uploadDetails
          )
        )
        .failed
        .futureValue
        .getMessage mustBe "mongodb down"

      verify(mockMonthlyReturnRepository).completeFileUpload(
        eqTo(zReference),
        eqTo(taxYear),
        eqTo(month),
        eqTo(upscanReference),
        eqTo(FileUploadStatus.UpscanSuccess),
        eqTo(Some(expectedFileUploadDetails)),
        eqTo(Some(downloadUrl)),
        eqTo(Option.empty[FileUploadFailureReason]),
        eqTo(Option.empty[String])
      )
    }

    "must fail when the repository fails for a FAILED callback" in {
      stubCompleteFileUpload(Future.failed(new RuntimeException("mongodb down")))

      service
        .monthlyReturnUpscanCallback(
          zReference,
          taxYear,
          month,
          UpscanFailure(
            reference = upscanReference,
            failureDetails = UpscanFailureDetails(
              failureReason = UpscanFailureReason.Rejected,
              message = "Upscan failure message"
            )
          )
        )
        .failed
        .futureValue
        .getMessage mustBe "mongodb down"

      verify(mockMonthlyReturnRepository).completeFileUpload(
        eqTo(zReference),
        eqTo(taxYear),
        eqTo(month),
        eqTo(upscanReference),
        eqTo(FileUploadStatus.UpscanRejected),
        eqTo(Option.empty[FileUploadDetails]),
        eqTo(Option.empty[String]),
        eqTo(Some(FileUploadFailureReason.Rejected)),
        eqTo(Some("Upscan failure message"))
      )
    }
  }

  private def stubCompleteFileUpload(result: Boolean): Unit =
    stubCompleteFileUpload(Future.successful(result))

  private def stubCompleteFileUpload(result: Future[Boolean]): Unit =
    when(
      mockMonthlyReturnRepository.completeFileUpload(
        any[String],
        any[String],
        any[String],
        any[String],
        any[FileUploadStatus],
        any[Option[FileUploadDetails]],
        any[Option[String]],
        any[Option[FileUploadFailureReason]],
        any[Option[String]]
      )
    ).thenReturn(result)
}
