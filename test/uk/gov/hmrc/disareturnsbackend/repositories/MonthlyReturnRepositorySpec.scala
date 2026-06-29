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

package uk.gov.hmrc.disareturnsbackend.repositories

import base.SpecBase
import org.mongodb.scala.SingleObservableFuture
import uk.gov.hmrc.disareturnsbackend.config.AppConfig
import uk.gov.hmrc.disareturnsbackend.models.*
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import java.time.{Clock, Instant, ZoneOffset}
import scala.concurrent.Future

class MonthlyReturnRepositorySpec extends SpecBase with DefaultPlayMongoRepositorySupport[MonthlyReturn] {

  override protected def databaseName: String = "disa-returns-backend-monthly-return-repository-test"

  private val fixedNow: Instant = testCreatedOn
  private val fixedClock: Clock = Clock.fixed(fixedNow, ZoneOffset.UTC)

  private lazy val appConfig: AppConfig = inject[AppConfig]

  override protected val repository: MonthlyReturnRepository =
    new MonthlyReturnRepository(mongoComponent, appConfig, fixedClock)

  override protected def afterAll(): Unit =
    try dropDatabase()
    finally super.afterAll()

  private val zReference      = testZReference
  private val taxYear         = yearOnlyTestTaxYear
  private val month           = testMonth
  private val existingUpdated = testExistingUpdatedOn

  "MonthlyReturnRepository" - {

    "get" - {

      "must return None when a MonthlyReturn does not exist" in {
        repository.get(zReference, taxYear, month).futureValue mustBe None
      }

      "must return a MonthlyReturn by zReference, taxYear and month" in {
        val monthlyReturn = buildMonthlyReturn()

        insertMonthlyReturn(monthlyReturn)

        repository.get(zReference, taxYear, month).futureValue.value mustBe monthlyReturn.copy(lastUpdated = fixedNow)
      }

      "must not return a MonthlyReturn with a different key" in {
        insertMonthlyReturn(buildMonthlyReturn())

        repository.get(zReference, taxYear, 6).futureValue mustBe None
      }
    }

    "create" - {

      "must create a MonthlyReturn with nilReturn set to false" in {
        repository.create(zReference, taxYear, month, testSubmissionId, nilReturn = false).futureValue mustBe Some(
          MonthlyReturn(
            zReference = zReference,
            submissionId = testSubmissionId,
            taxYear = taxYear,
            month = month,
            createdOn = fixedNow,
            nilReturn = false,
            fileUploads = Nil,
            lastUpdated = fixedNow
          )
        )

        repository.get(zReference, taxYear, month).futureValue.value mustBe MonthlyReturn(
          zReference = zReference,
          submissionId = testSubmissionId,
          taxYear = taxYear,
          month = month,
          createdOn = fixedNow,
          nilReturn = false,
          fileUploads = Nil,
          lastUpdated = fixedNow
        )
      }

      "must create a MonthlyReturn with nilReturn set to true and no file uploads" in {
        repository.create(zReference, taxYear, month, testSubmissionId, nilReturn = true).futureValue mustBe Some(
          MonthlyReturn(
            zReference = zReference,
            submissionId = testSubmissionId,
            taxYear = taxYear,
            month = month,
            createdOn = fixedNow,
            nilReturn = true,
            fileUploads = Nil,
            lastUpdated = fixedNow
          )
        )

        repository.get(zReference, taxYear, month).futureValue.value mustBe MonthlyReturn(
          zReference = zReference,
          submissionId = testSubmissionId,
          taxYear = taxYear,
          month = month,
          createdOn = fixedNow,
          nilReturn = true,
          fileUploads = Nil,
          lastUpdated = fixedNow
        )
      }

      "must return None when a MonthlyReturn already exists for the same key" in {
        repository
          .create(zReference, taxYear, month, testSubmissionId, nilReturn = true)
          .futureValue
          .value
          .nilReturn mustBe true

        repository.create(zReference, taxYear, month, testSubmissionId, nilReturn = false).futureValue mustBe None

        repository.get(zReference, taxYear, month).futureValue.value.nilReturn mustBe true
      }
    }

    "updateNilReturn" - {

      "must update nilReturn atomically and clear file uploads" in {
        val monthlyReturn = buildMonthlyReturn(fileUploads = List(createdFileUpload(testUploadReference)))
        insertMonthlyReturn(monthlyReturn)

        repository.updateNilReturn(zReference, taxYear, month, nilReturn = true).futureValue mustBe
          UpdateNilReturnRepositoryResult.NilReturnUpdated(
            monthlyReturn.copy(nilReturn = true, fileUploads = Nil, lastUpdated = fixedNow)
          )
      }

      "must return MonthlyReturnNotFound when no monthly return exists" in {
        repository.updateNilReturn(zReference, taxYear, month, nilReturn = true).futureValue mustBe
          UpdateNilReturnRepositoryResult.MonthlyReturnNotFound
      }

      "must not update lastUpdated when nilReturn is unchanged" in {
        val monthlyReturn = buildMonthlyReturn(nilReturn = false, lastUpdated = existingUpdated)
        insertMonthlyReturn(monthlyReturn)

        repository.updateNilReturn(zReference, taxYear, month, nilReturn = false).futureValue mustBe
          UpdateNilReturnRepositoryResult.NilReturnUpdated(monthlyReturn.copy(lastUpdated = fixedNow))
      }
    }

    "createFileUpload" - {

      "must append a new upload without replacing existing uploads" in {
        val existingReference = "existing-reference"
        insertMonthlyReturn(buildMonthlyReturn(fileUploads = List(createdFileUpload(existingReference))))

        val result = repository.createFileUpload(zReference, taxYear, month, testUploadReference).futureValue

        val updatedReturn = result.asInstanceOf[CreateFileUploadRepositoryResult.FileUploadCreated].monthlyReturn
        updatedReturn.fileUploads.map(_.reference) must contain theSameElementsInOrderAs List(
          existingReference,
          testUploadReference
        )
        updatedReturn.lastUpdated mustBe fixedNow
      }

      "must return FileUploadAlreadyExists when the reference already exists" in {
        insertMonthlyReturn(buildMonthlyReturn(fileUploads = List(createdFileUpload(testUploadReference))))

        repository.createFileUpload(zReference, taxYear, month, testUploadReference).futureValue mustBe
          CreateFileUploadRepositoryResult.FileUploadAlreadyExists
      }

      "must return MonthlyReturnNotFound for nil returns" in {
        insertMonthlyReturn(buildMonthlyReturn(nilReturn = true))

        repository.createFileUpload(zReference, taxYear, month, testUploadReference).futureValue mustBe
          CreateFileUploadRepositoryResult.MonthlyReturnNotFound
      }

      "must preserve concurrent upload creates for different references" in {
        val firstReference  = "first-reference"
        val secondReference = "second-reference"
        insertMonthlyReturn(buildMonthlyReturn())

        val firstCreate  = repository.createFileUpload(zReference, taxYear, month, firstReference)
        val secondCreate = repository.createFileUpload(zReference, taxYear, month, secondReference)

        Future.sequence(Seq(firstCreate, secondCreate)).futureValue

        val stored = repository.get(zReference, taxYear, month).futureValue.value
        stored.fileUploads.map(_.reference) must contain theSameElementsAs Seq(firstReference, secondReference)
      }
    }

    "deleteFileUpload" - {

      "must remove only the matching upload" in {
        val keptReference = "kept-reference"
        insertMonthlyReturn(
          buildMonthlyReturn(fileUploads =
            List(createdFileUpload(testUploadReference), createdFileUpload(keptReference))
          )
        )

        repository.deleteFileUpload(zReference, taxYear, month, testUploadReference).futureValue mustBe true

        val stored = repository.get(zReference, taxYear, month).futureValue.value
        stored.fileUploads.map(_.reference) mustBe List(keptReference)
        stored.lastUpdated mustBe fixedNow
      }

      "must return false when the upload does not exist" in {
        insertMonthlyReturn(buildMonthlyReturn())

        repository.deleteFileUpload(zReference, taxYear, month, testUploadReference).futureValue mustBe false
      }
    }

    "completeUpscan" - {

      "must update only the matching upload and preserve other uploads" in {
        val otherReference = "other-reference"
        insertMonthlyReturn(
          buildMonthlyReturn(fileUploads =
            List(createdFileUpload(testUploadReference), createdFileUpload(otherReference))
          )
        )

        repository
          .completeUpscan(
            zReference,
            taxYear,
            month,
            testUploadReference,
            FileUploadStatus.UpscanSuccess,
            Some(fileUploadDetails)
          )
          .futureValue mustBe true

        val stored          = repository.get(zReference, taxYear, month).futureValue.value
        val completedUpload = stored.getFileUpload(testUploadReference).value
        completedUpload.status mustBe FileUploadStatus.UpscanSuccess
        completedUpload.fileUploadDetails.value.upscanCompletedOn mustBe Some(fixedNow)
        stored.getFileUpload(otherReference).value.status mustBe FileUploadStatus.Created
      }

      "must return false when the upload is not in CREATED status" in {
        insertMonthlyReturn(buildMonthlyReturn(fileUploads = List(completedFileUpload(testUploadReference))))

        repository
          .completeUpscan(
            zReference,
            taxYear,
            month,
            testUploadReference,
            FileUploadStatus.UpscanSuccess,
            Some(fileUploadDetails)
          )
          .futureValue mustBe false
      }

      "must preserve uploads added after an earlier read while completing another upload" in {
        val laterReference = "later-reference"
        val earlierRead    = buildMonthlyReturn(fileUploads = List(createdFileUpload(testUploadReference)))
        insertMonthlyReturn(earlierRead)
        repository.createFileUpload(zReference, taxYear, month, laterReference).futureValue

        repository
          .completeUpscan(
            zReference,
            taxYear,
            month,
            testUploadReference,
            FileUploadStatus.UpscanSuccess,
            Some(fileUploadDetails)
          )
          .futureValue mustBe true

        val stored = repository.get(zReference, taxYear, month).futureValue.value
        stored.fileUploads.map(_.reference) must contain theSameElementsAs Seq(testUploadReference, laterReference)
        stored.getFileUpload(testUploadReference).value.status mustBe FileUploadStatus.UpscanSuccess
        stored.getFileUpload(laterReference).value.status mustBe FileUploadStatus.Created
      }
    }

    "updateFileUploadProcessingDetails" - {

      "must update processing details on the matching upload only" in {
        val otherReference = "other-reference"
        val validation     = FileUploadValidationResult(1, 0, FileUploadValidationStatus.ValidationSuccess)
        insertMonthlyReturn(
          buildMonthlyReturn(fileUploads =
            List(completedFileUpload(testUploadReference), completedFileUpload(otherReference))
          )
        )

        repository
          .updateFileUploadProcessingDetails(
            zReference,
            taxYear,
            month,
            testUploadReference,
            validation,
            Some("original-location"),
            Some("errors-location")
          )
          .futureValue mustBe true

        val stored        = repository.get(zReference, taxYear, month).futureValue.value
        val updatedUpload = stored.getFileUpload(testUploadReference).value
        updatedUpload.status mustBe FileUploadStatus.ValidationSuccess
        updatedUpload.fileUploadDetails.value.validation mustBe Some(validation)
        stored.getFileUpload(otherReference).value.status mustBe FileUploadStatus.UpscanSuccess
      }

      "must return false when the upload has no details" in {
        insertMonthlyReturn(buildMonthlyReturn(fileUploads = List(createdFileUpload(testUploadReference))))

        repository
          .updateFileUploadProcessingDetails(
            zReference,
            taxYear,
            month,
            testUploadReference,
            FileUploadValidationResult(1, 0, FileUploadValidationStatus.ValidationSuccess),
            Some("original-location"),
            None
          )
          .futureValue mustBe false
      }

      "must return false when the upload is no longer UpscanSuccess" in {
        val expiredUpload = completedFileUpload(testUploadReference).copy(status = FileUploadStatus.UpscanExpired)
        insertMonthlyReturn(buildMonthlyReturn(fileUploads = List(expiredUpload)))

        repository
          .updateFileUploadProcessingDetails(
            zReference,
            taxYear,
            month,
            testUploadReference,
            FileUploadValidationResult(1, 0, FileUploadValidationStatus.ValidationSuccess),
            Some("original-location"),
            None
          )
          .futureValue mustBe false

        repository.get(zReference, taxYear, month).futureValue.value.getFileUpload(testUploadReference).value mustBe
          expiredUpload
      }

      "must preserve uploads added after an earlier worker read while updating processing details" in {
        val laterReference = "later-reference"
        val validation     = FileUploadValidationResult(1, 0, FileUploadValidationStatus.ValidationSuccess)
        val earlierRead    = buildMonthlyReturn(fileUploads = List(completedFileUpload(testUploadReference)))
        insertMonthlyReturn(earlierRead)
        repository.createFileUpload(zReference, taxYear, month, laterReference).futureValue

        repository
          .updateFileUploadProcessingDetails(
            zReference,
            taxYear,
            month,
            testUploadReference,
            validation,
            Some("original-location"),
            None
          )
          .futureValue mustBe true

        val stored = repository.get(zReference, taxYear, month).futureValue.value
        stored.fileUploads.map(_.reference) must contain theSameElementsAs Seq(testUploadReference, laterReference)
        stored.getFileUpload(testUploadReference).value.status mustBe FileUploadStatus.ValidationSuccess
        stored.getFileUpload(laterReference).value.status mustBe FileUploadStatus.Created
      }
    }

    "markUpscanExpired" - {

      "must mark only the matching successful upload as expired" in {
        val otherReference = "other-reference"
        insertMonthlyReturn(
          buildMonthlyReturn(fileUploads =
            List(completedFileUpload(testUploadReference), completedFileUpload(otherReference))
          )
        )

        repository.markUpscanExpired(zReference, taxYear, month, testUploadReference).futureValue mustBe true

        val stored = repository.get(zReference, taxYear, month).futureValue.value
        stored.getFileUpload(testUploadReference).value.status mustBe FileUploadStatus.UpscanExpired
        stored.getFileUpload(otherReference).value.status mustBe FileUploadStatus.UpscanSuccess
      }

      "must return false when the upload is not successful" in {
        insertMonthlyReturn(buildMonthlyReturn(fileUploads = List(createdFileUpload(testUploadReference))))

        repository.markUpscanExpired(zReference, taxYear, month, testUploadReference).futureValue mustBe false
      }
    }

  }

  private def insertMonthlyReturn(monthlyReturn: MonthlyReturn): Unit =
    repository.collection.insertOne(monthlyReturn.copy(lastUpdated = fixedNow)).toFuture().futureValue

  private def buildMonthlyReturn(
    nilReturn: Boolean = false,
    fileUploads: List[FileUpload] = Nil,
    lastUpdated: Instant = existingUpdated
  ): MonthlyReturn =
    MonthlyReturn(
      zReference = zReference,
      submissionId = testSubmissionId,
      taxYear = taxYear,
      month = month,
      createdOn = lastUpdated,
      nilReturn = nilReturn,
      fileUploads = fileUploads,
      lastUpdated = lastUpdated
    )

  private def createdFileUpload(reference: String): FileUpload =
    FileUpload(
      reference = reference,
      status = FileUploadStatus.Created,
      createdOn = fixedNow
    )

  private def completedFileUpload(reference: String): FileUpload =
    FileUpload(
      reference = reference,
      status = FileUploadStatus.UpscanSuccess,
      createdOn = fixedNow,
      fileUploadDetails = Some(fileUploadDetails)
    )

  private val fileUploadDetails = FileUploadDetails(
    fileName = testFileName,
    fileMimeType = testFileMimeType,
    uploadTimestamp = fixedNow,
    checksum = testChecksum,
    size = testFileSize,
    upscanDownloadUrl = testDownloadUrl
  )

}
