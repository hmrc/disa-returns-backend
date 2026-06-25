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
import uk.gov.hmrc.disareturnsbackend.repositories.{MonthlyReturnFileUploadWorkItemRepository, MonthlyReturnRepository}
import uk.gov.hmrc.mongo.workitem.{ProcessingStatus, WorkItem}

import java.time.{Clock, ZoneOffset}
import scala.concurrent.Future

class UpscanCallbackServiceSpec extends SpecBase with BeforeAndAfterEach {

  private val mockMonthlyReturnRepository                   = mock[MonthlyReturnRepository]
  private val mockMonthlyReturnFileUploadWorkItemRepository = mock[MonthlyReturnFileUploadWorkItemRepository]
  private val upscanCallbackMapper: UpscanCallbackMapper    = new UpscanCallbackMapperImpl()
  private val service                                       = new UpscanCallbackService(
    mockMonthlyReturnRepository,
    mockMonthlyReturnFileUploadWorkItemRepository,
    upscanCallbackMapper,
    Clock.fixed(testCreatedOn, ZoneOffset.UTC)
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
    reset(mockMonthlyReturnRepository, mockMonthlyReturnFileUploadWorkItemRepository)
  }

  "monthlyReturnUpscanCallback" - {

    "must complete a file upload as UPSCAN_SUCCESS for a READY callback" in {
      stubCompleteUpscan(result = true)
      stubEnqueueMonthlyReturnFileUploadWorkItem()

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

      verifyMonthlyReturnUpserted(FileUploadStatus.UpscanSuccess, Some(expectedFileUploadDetails))
      verifyEnqueueMonthlyReturnFileUploadWorkItem()
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

      verifyNoMonthlyReturnFileUploadWorkItemEnqueued()
    }

    "must complete a file upload as DUPLICATE and not enqueue processing when the checksum already exists" in {
      val monthlyReturnWithDuplicateChecksum = monthlyReturnWithCreatedUpload.copy(
        fileUploads = monthlyReturnWithCreatedUpload.fileUploads :+ FileUpload(
          reference = "existing-reference",
          status = FileUploadStatus.ValidationSuccess,
          createdOn = testCreatedOn,
          fileUploadDetails = Some(expectedFileUploadDetails)
        )
      )

      stubGetMonthlyReturn(Future.successful(Some(monthlyReturnWithDuplicateChecksum)))
      when(mockMonthlyReturnRepository.upsert(any[MonthlyReturn])).thenReturn(Future.successful(true))

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

      verifyMonthlyReturnUpserted(
        FileUploadStatus.Duplicate,
        Some(expectedFileUploadDetails),
        monthlyReturn = monthlyReturnWithDuplicateChecksum
      )
      verifyNoMonthlyReturnFileUploadWorkItemEnqueued()
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

      verifyNoMonthlyReturnUpserted()
      verifyNoMonthlyReturnFileUploadWorkItemEnqueued()
    }

    "must not complete a file upload when no matching CREATED upload exists" in {
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

      verifyNoMonthlyReturnUpserted()
      verifyNoMonthlyReturnFileUploadWorkItemEnqueued()
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

      verifyNoMonthlyReturnUpserted()
      verifyNoMonthlyReturnFileUploadWorkItemEnqueued()
    }

    "must complete a file upload when the CREATED upload exists" in {
      val declaredReturn = monthlyReturn.copy(
        fileUploads = List(
          FileUpload(
            reference = upscanReference,
            status = FileUploadStatus.Created,
            createdOn = testCreatedOn
          )
        )
      )

      stubGetMonthlyReturn(Future.successful(Some(declaredReturn)))
      when(mockMonthlyReturnRepository.upsert(any[MonthlyReturn])).thenReturn(Future.successful(true))
      stubEnqueueMonthlyReturnFileUploadWorkItem()

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

      verifyMonthlyReturnUpserted(FileUploadStatus.UpscanSuccess, Some(expectedFileUploadDetails))
      verifyEnqueueMonthlyReturnFileUploadWorkItem()
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

        verifyMonthlyReturnUpserted(
          status = fileUploadStatus,
          fileUploadDetails = None,
          failureReason = Some(fileUploadFailureReason),
          failureMessage = Some(testUpscanFailureMessage)
        )
        verifyNoMonthlyReturnFileUploadWorkItemEnqueued()
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

      verifyMonthlyReturnUpserted(FileUploadStatus.UpscanSuccess, Some(expectedFileUploadDetails))
      verifyNoMonthlyReturnFileUploadWorkItemEnqueued()
    }

    "must fail when adding a file upload work item fails for a READY callback" in {
      stubCompleteUpscan(result = true)
      stubEnqueueMonthlyReturnFileUploadWorkItem(Future.failed(new RuntimeException(testMongoDownMessage)))

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

      verifyMonthlyReturnUpserted(FileUploadStatus.UpscanSuccess, Some(expectedFileUploadDetails))
      verifyEnqueueMonthlyReturnFileUploadWorkItem()
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

      verifyMonthlyReturnUpserted(
        status = FileUploadStatus.UpscanRejected,
        fileUploadDetails = None,
        failureReason = Some(FileUploadFailureReason.Rejected),
        failureMessage = Some(testUpscanFailureMessage)
      )
      verifyNoMonthlyReturnFileUploadWorkItemEnqueued()
    }
  }

  private def stubEnqueueMonthlyReturnFileUploadWorkItem(): Unit =
    stubEnqueueMonthlyReturnFileUploadWorkItem(
      Future.successful(
        WorkItem(
          id = new ObjectId(),
          receivedAt = testCreatedOn,
          updatedAt = testCreatedOn,
          availableAt = testCreatedOn,
          status = ProcessingStatus.ToDo,
          failureCount = 0,
          item = MonthlyReturnFileUploadWorkItem(zReference, taxYear, month, upscanReference)
        )
      )
    )

  private def stubEnqueueMonthlyReturnFileUploadWorkItem(
    result: Future[WorkItem[MonthlyReturnFileUploadWorkItem]]
  ): Unit =
    when(
      mockMonthlyReturnFileUploadWorkItemRepository.enqueue(
        any[String],
        any[String],
        any[Int],
        any[String]
      )
    ).thenReturn(result)

  private def verifyEnqueueMonthlyReturnFileUploadWorkItem(): Unit =
    verify(mockMonthlyReturnFileUploadWorkItemRepository).enqueue(
      eqTo(zReference),
      eqTo(taxYear),
      eqTo(month),
      eqTo(upscanReference)
    )

  private def verifyNoMonthlyReturnFileUploadWorkItemEnqueued(): Unit =
    verify(mockMonthlyReturnFileUploadWorkItemRepository, never()).enqueue(
      any[String],
      any[String],
      any[Int],
      any[String]
    )

  private def stubCompleteUpscan(result: Boolean): Unit =
    stubCompleteUpscan(Future.successful(result))

  private def stubCompleteUpscan(result: Future[Boolean]): Unit =
    stubGetMonthlyReturn(Future.successful(Some(monthlyReturnWithCreatedUpload)))

    when(mockMonthlyReturnRepository.upsert(any[MonthlyReturn])).thenReturn(result)

  private def verifyMonthlyReturnUpserted(
    status: FileUploadStatus,
    fileUploadDetails: Option[FileUploadDetails],
    failureReason: Option[FileUploadFailureReason] = None,
    failureMessage: Option[String] = None,
    monthlyReturn: MonthlyReturn = monthlyReturnWithCreatedUpload
  ): Unit =
    verify(mockMonthlyReturnRepository).upsert(
      eqTo(
        monthlyReturn.completeUpscan(
          reference = upscanReference,
          status = status,
          upscanCompletedOn = testCreatedOn,
          fileUploadDetails = fileUploadDetails,
          failureReason = failureReason,
          failureMessage = failureMessage
        )
      )
    )

  private def verifyNoMonthlyReturnUpserted(): Unit =
    verify(mockMonthlyReturnRepository, never()).upsert(any[MonthlyReturn])

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
