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
import org.bson.types.ObjectId
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.{never, reset, verify, when}
import org.scalatest.BeforeAndAfterEach
import uk.gov.hmrc.disareturnsbackend.mappers.{UpscanCallbackMapper, UpscanCallbackMapperImpl}
import uk.gov.hmrc.disareturnsbackend.models.*
import uk.gov.hmrc.disareturnsbackend.repositories.{FileUploadWorkItemRepository, MonthlyReturnRepository}
import uk.gov.hmrc.mongo.workitem.{ProcessingStatus, WorkItem}

import scala.concurrent.Future

class UpscanCallbackServiceSpec extends SpecBase with BeforeAndAfterEach {

  private val mockMonthlyReturnRepository                = mock[MonthlyReturnRepository]
  private val mockFileUploadWorkItemRepository           = mock[FileUploadWorkItemRepository]
  private val upscanCallbackMapper: UpscanCallbackMapper = new UpscanCallbackMapperImpl()
  private val service                                    = new UpscanCallbackService(
    mockMonthlyReturnRepository,
    mockFileUploadWorkItemRepository,
    upscanCallbackMapper
  )

  private val zReference                = testZReference
  private val taxYear                   = yearOnlyTestTaxYear
  private val month                     = testMonth
  private val upscanReference           = testUploadReference
  private val upscanDownloadUrl         = testDownloadUrl
  private val uploadDetails             = UpscanDetails(
    fileName = testFileName,
    fileMimeType = testFileMimeType,
    uploadTimestamp = testCreatedOn,
    checksum = testChecksum,
    size = testFileSize
  )
  private val expectedFileUploadDetails = FileUploadDetails(
    fileName = uploadDetails.fileName,
    fileMimeType = uploadDetails.fileMimeType,
    uploadTimestamp = uploadDetails.uploadTimestamp,
    checksum = uploadDetails.checksum,
    size = uploadDetails.size,
    upscanDownloadUrl = upscanDownloadUrl
  )
  private val monthlyReturn             = MonthlyReturn(
    zReference = zReference,
    submissionId = testSubmissionId,
    taxYear = taxYear,
    month = month,
    createdOn = testExistingUpdatedOn.minusSeconds(3600),
    nilReturn = false,
    fileUploads = Nil,
    lastUpdated = testExistingUpdatedOn
  )

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockMonthlyReturnRepository, mockFileUploadWorkItemRepository)
  }

  "monthlyReturnUpscanCallback" - {

    "must complete a file upload as UPSCAN_SUCCESS for a READY callback" in {
      stubCompleteUpscan(result = true)
      stubEnqueueFileUploadWorkItem()

      service
        .monthlyReturnUpscanCallback(
          zReference,
          taxYear,
          month,
          UpscanSuccess(
            reference = upscanReference,
            downloadUrl = upscanDownloadUrl,
            uploadDetails = uploadDetails
          )
        )
        .futureValue

      verify(mockMonthlyReturnRepository).completeUpscan(
        eqTo(zReference),
        eqTo(taxYear),
        eqTo(month),
        eqTo(upscanReference),
        eqTo(FileUploadStatus.UpscanSuccess),
        eqTo(Some(expectedFileUploadDetails)),
        eqTo(Option.empty[FileUploadFailureReason]),
        eqTo(Option.empty[String])
      )
      verifyEnqueueFileUploadWorkItem()
    }

    "must not enqueue a file upload work item when no monthly return is updated" in {
      stubCompleteUpscan(result = false)

      service
        .monthlyReturnUpscanCallback(
          zReference,
          taxYear,
          month,
          UpscanSuccess(
            reference = upscanReference,
            downloadUrl = upscanDownloadUrl,
            uploadDetails = uploadDetails
          )
        )
        .futureValue

      verifyNoFileUploadWorkItemEnqueued()
    }

    "must not complete a file upload for a nil return" in {
      stubGetMonthlyReturn(Future.successful(Some(monthlyReturn.copy(nilReturn = true))))

      service
        .monthlyReturnUpscanCallback(
          zReference,
          taxYear,
          month,
          UpscanSuccess(
            reference = upscanReference,
            downloadUrl = upscanDownloadUrl,
            uploadDetails = uploadDetails
          )
        )
        .futureValue

      verify(mockMonthlyReturnRepository, never()).completeUpscan(
        any[String],
        any[String],
        any[Int],
        any[String],
        any[FileUploadStatus],
        any[Option[FileUploadDetails]],
        any[Option[FileUploadFailureReason]],
        any[Option[String]]
      )
      verifyNoFileUploadWorkItemEnqueued()
    }

    "must not complete a file upload when the reference does not exist" in {
      stubGetMonthlyReturn(Future.successful(Some(monthlyReturn)))

      service
        .monthlyReturnUpscanCallback(
          zReference,
          taxYear,
          month,
          UpscanSuccess(
            reference = upscanReference,
            downloadUrl = upscanDownloadUrl,
            uploadDetails = uploadDetails
          )
        )
        .futureValue

      verify(mockMonthlyReturnRepository, never()).completeUpscan(
        any[String],
        any[String],
        any[Int],
        any[String],
        any[FileUploadStatus],
        any[Option[FileUploadDetails]],
        any[Option[FileUploadFailureReason]],
        any[Option[String]]
      )
      verifyNoFileUploadWorkItemEnqueued()
    }

    "must not complete a file upload for a declared return when the reference does not exist" in {
      stubGetMonthlyReturn(Future.successful(Some(monthlyReturn.copy(declaredOn = Some(testCreatedOn)))))

      service
        .monthlyReturnUpscanCallback(
          zReference,
          taxYear,
          month,
          UpscanSuccess(
            reference = upscanReference,
            downloadUrl = upscanDownloadUrl,
            uploadDetails = uploadDetails
          )
        )
        .futureValue

      verify(mockMonthlyReturnRepository, never()).completeUpscan(
        any[String],
        any[String],
        any[Int],
        any[String],
        any[FileUploadStatus],
        any[Option[FileUploadDetails]],
        any[Option[FileUploadFailureReason]],
        any[Option[String]]
      )
      verifyNoFileUploadWorkItemEnqueued()
    }

    "must complete a file upload for a declared return when the CREATED upload exists from before declaration" in {
      val declaredReturn = monthlyReturn.copy(
        declaredOn = Some(testCreatedOn.plusSeconds(1)),
        fileUploads = List(
          FileUpload(
            reference = upscanReference,
            status = FileUploadStatus.Created,
            createdOn = testCreatedOn
          )
        )
      )

      stubGetMonthlyReturn(Future.successful(Some(declaredReturn)))
      when(
        mockMonthlyReturnRepository.completeUpscan(
          any[String],
          any[String],
          any[Int],
          any[String],
          any[FileUploadStatus],
          any[Option[FileUploadDetails]],
          any[Option[FileUploadFailureReason]],
          any[Option[String]]
        )
      ).thenReturn(Future.successful(true))
      stubEnqueueFileUploadWorkItem()

      service
        .monthlyReturnUpscanCallback(
          zReference,
          taxYear,
          month,
          UpscanSuccess(
            reference = upscanReference,
            downloadUrl = upscanDownloadUrl,
            uploadDetails = uploadDetails
          )
        )
        .futureValue

      verify(mockMonthlyReturnRepository).completeUpscan(
        eqTo(zReference),
        eqTo(taxYear),
        eqTo(month),
        eqTo(upscanReference),
        eqTo(FileUploadStatus.UpscanSuccess),
        eqTo(Some(expectedFileUploadDetails)),
        eqTo(Option.empty[FileUploadFailureReason]),
        eqTo(Option.empty[String])
      )
      verifyEnqueueFileUploadWorkItem()
    }

    Seq(
      UpscanFailureReason.Quarantine -> (FileUploadStatus.UpscanQuarantine, FileUploadFailureReason.Quarantine),
      UpscanFailureReason.Rejected   -> (FileUploadStatus.UpscanRejected, FileUploadFailureReason.Rejected),
      UpscanFailureReason.Unknown    -> (FileUploadStatus.UpscanUnknown, FileUploadFailureReason.Unknown)
    ).foreach { case (upscanFailureReason, (fileUploadStatus, fileUploadFailureReason)) =>
      s"must complete a file upload as ${fileUploadStatus.value} for a ${upscanFailureReason.value} callback" in {
        stubCompleteUpscan(result = false)

        service
          .monthlyReturnUpscanCallback(
            zReference,
            taxYear,
            month,
            UpscanFailure(
              reference = upscanReference,
              failureDetails = UpscanFailureDetails(
                failureReason = upscanFailureReason,
                message = testUpscanFailureMessage
              )
            )
          )
          .futureValue

        verify(mockMonthlyReturnRepository).completeUpscan(
          eqTo(zReference),
          eqTo(taxYear),
          eqTo(month),
          eqTo(upscanReference),
          eqTo(fileUploadStatus),
          eqTo(Option.empty[FileUploadDetails]),
          eqTo(Some(fileUploadFailureReason)),
          eqTo(Some(testUpscanFailureMessage))
        )
        verifyNoFileUploadWorkItemEnqueued()
      }
    }

    "must fail when the repository fails for a READY callback" in {
      stubCompleteUpscan(Future.failed(new RuntimeException(testMongoDownMessage)))

      service
        .monthlyReturnUpscanCallback(
          zReference,
          taxYear,
          month,
          UpscanSuccess(
            reference = upscanReference,
            downloadUrl = upscanDownloadUrl,
            uploadDetails = uploadDetails
          )
        )
        .failed
        .futureValue
        .getMessage mustBe testMongoDownMessage

      verify(mockMonthlyReturnRepository).completeUpscan(
        eqTo(zReference),
        eqTo(taxYear),
        eqTo(month),
        eqTo(upscanReference),
        eqTo(FileUploadStatus.UpscanSuccess),
        eqTo(Some(expectedFileUploadDetails)),
        eqTo(Option.empty[FileUploadFailureReason]),
        eqTo(Option.empty[String])
      )
      verifyNoFileUploadWorkItemEnqueued()
    }

    "must fail when adding a file upload work item fails for a READY callback" in {
      stubCompleteUpscan(result = true)
      stubEnqueueFileUploadWorkItem(Future.failed(new RuntimeException(testMongoDownMessage)))

      service
        .monthlyReturnUpscanCallback(
          zReference,
          taxYear,
          month,
          UpscanSuccess(
            reference = upscanReference,
            downloadUrl = upscanDownloadUrl,
            uploadDetails = uploadDetails
          )
        )
        .failed
        .futureValue
        .getMessage mustBe testMongoDownMessage

      verify(mockMonthlyReturnRepository).completeUpscan(
        eqTo(zReference),
        eqTo(taxYear),
        eqTo(month),
        eqTo(upscanReference),
        eqTo(FileUploadStatus.UpscanSuccess),
        eqTo(Some(expectedFileUploadDetails)),
        eqTo(Option.empty[FileUploadFailureReason]),
        eqTo(Option.empty[String])
      )
      verifyEnqueueFileUploadWorkItem()
    }

    "must fail when the repository fails for a FAILED callback" in {
      stubCompleteUpscan(Future.failed(new RuntimeException(testMongoDownMessage)))

      service
        .monthlyReturnUpscanCallback(
          zReference,
          taxYear,
          month,
          UpscanFailure(
            reference = upscanReference,
            failureDetails = UpscanFailureDetails(
              failureReason = UpscanFailureReason.Rejected,
              message = testUpscanFailureMessage
            )
          )
        )
        .failed
        .futureValue
        .getMessage mustBe testMongoDownMessage

      verify(mockMonthlyReturnRepository).completeUpscan(
        eqTo(zReference),
        eqTo(taxYear),
        eqTo(month),
        eqTo(upscanReference),
        eqTo(FileUploadStatus.UpscanRejected),
        eqTo(Option.empty[FileUploadDetails]),
        eqTo(Some(FileUploadFailureReason.Rejected)),
        eqTo(Some(testUpscanFailureMessage))
      )
      verifyNoFileUploadWorkItemEnqueued()
    }
  }

  private def stubEnqueueFileUploadWorkItem(): Unit =
    stubEnqueueFileUploadWorkItem(
      Future.successful(
        WorkItem(
          id = new ObjectId(),
          receivedAt = testCreatedOn,
          updatedAt = testCreatedOn,
          availableAt = testCreatedOn,
          status = ProcessingStatus.ToDo,
          failureCount = 0,
          item = FileUploadWorkItem(zReference, taxYear, month, upscanReference)
        )
      )
    )

  private def stubEnqueueFileUploadWorkItem(result: Future[WorkItem[FileUploadWorkItem]]): Unit =
    when(
      mockFileUploadWorkItemRepository.enqueue(
        any[String],
        any[String],
        any[Int],
        any[String]
      )
    ).thenReturn(result)

  private def verifyEnqueueFileUploadWorkItem(): Unit =
    verify(mockFileUploadWorkItemRepository).enqueue(
      eqTo(zReference),
      eqTo(taxYear),
      eqTo(month),
      eqTo(upscanReference)
    )

  private def verifyNoFileUploadWorkItemEnqueued(): Unit =
    verify(mockFileUploadWorkItemRepository, never()).enqueue(
      any[String],
      any[String],
      any[Int],
      any[String]
    )

  private def stubCompleteUpscan(result: Boolean): Unit =
    stubCompleteUpscan(Future.successful(result))

  private def stubCompleteUpscan(result: Future[Boolean]): Unit =
    stubGetMonthlyReturn(Future.successful(Some(monthlyReturnWithCreatedUpload)))

    when(
      mockMonthlyReturnRepository.completeUpscan(
        any[String],
        any[String],
        any[Int],
        any[String],
        any[FileUploadStatus],
        any[Option[FileUploadDetails]],
        any[Option[FileUploadFailureReason]],
        any[Option[String]]
      )
    ).thenReturn(result)

  private def monthlyReturnWithCreatedUpload: MonthlyReturn =
    monthlyReturn.copy(
      fileUploads = List(
        FileUpload(
          reference = upscanReference,
          status = FileUploadStatus.Created,
          createdOn = testCreatedOn
        )
      )
    )

  private def stubGetMonthlyReturn(result: Future[Option[MonthlyReturn]]): Unit =
    when(
      mockMonthlyReturnRepository.get(
        any[String],
        any[String],
        any[Int]
      )
    ).thenReturn(result)
}
