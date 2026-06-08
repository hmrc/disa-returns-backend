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
import uk.gov.hmrc.disareturnsbackend.config.AppConfig
import uk.gov.hmrc.disareturnsbackend.models.FileUploadFailureReason.Rejected
import uk.gov.hmrc.disareturnsbackend.models.FileUploadStatus.*
import uk.gov.hmrc.disareturnsbackend.models.*
import uk.gov.hmrc.disareturnsbackend.repositories.MonthlyReturnRepository.CreateFileUploadRepositoryResult.*
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import java.time.{Clock, Instant, ZoneOffset}

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

  private val zReference        = testZReference
  private val taxYear           = yearOnlyTestTaxYear
  private val month             = testMonth
  private val uploadReference   = testUploadReference
  private val existingUpdated   = testExistingUpdatedOn
  private val createdOn         = testRepositoryCreatedOn
  private val fileUploadDetails = FileUploadDetails(
    fileName = testFileName,
    fileMimeType = testFileMimeType,
    uploadTimestamp = testCreatedOn,
    checksum = testChecksum,
    size = testFileSize,
    upscanDownloadUrl = testDownloadUrl
  )

  "MonthlyReturnRepository" - {

    "get" - {

      "must return None when a MonthlyReturn does not exist" in {
        repository.get(zReference, taxYear, month).futureValue mustBe None
      }

      "must return a MonthlyReturn by zReference, taxYear and month" in {
        val monthlyReturn = buildMonthlyReturn()

        repository.upsert(monthlyReturn).futureValue

        repository.get(zReference, taxYear, month).futureValue.value mustBe monthlyReturn.copy(lastUpdated = fixedNow)
      }

      "must not return a MonthlyReturn with a different key" in {
        repository.upsert(buildMonthlyReturn()).futureValue

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

    "upsert" - {

      "must insert a MonthlyReturn and set lastUpdated" in {
        val monthlyReturn = buildMonthlyReturn(lastUpdated = existingUpdated)

        repository.upsert(monthlyReturn).futureValue mustBe true

        repository.get(zReference, taxYear, month).futureValue.value mustBe monthlyReturn.copy(lastUpdated = fixedNow)
      }

      "must replace an existing MonthlyReturn for the same key" in {
        val existing    = buildMonthlyReturn(
          fileUploads = List(createdFileUpload(reference = "old-reference"))
        )
        val replacement = buildMonthlyReturn(
          fileUploads = List(createdFileUpload(reference = "new-reference"))
        )

        repository.upsert(existing).futureValue
        repository.upsert(replacement).futureValue mustBe true

        val stored = repository.get(zReference, taxYear, month).futureValue.value
        stored.fileUploads.map(_.reference) mustBe List("new-reference")
        stored.lastUpdated mustBe fixedNow
      }
    }

    "updateNilReturn" - {

      "must set nilReturn to true and remove file uploads" in {
        repository
          .upsert(buildMonthlyReturn(fileUploads = List(createdFileUpload(reference = "existing-reference"))))
          .futureValue

        val result = repository.updateNilReturn(zReference, taxYear, month, nilReturn = true).futureValue.value

        result.nilReturn mustBe true
        result.fileUploads mustBe Nil
        result.createdOn mustBe existingUpdated
        result.lastUpdated mustBe fixedNow

        repository.get(zReference, taxYear, month).futureValue.value mustBe result
      }

      "must set nilReturn to false" in {
        repository.upsert(buildMonthlyReturn(nilReturn = true)).futureValue

        val result = repository.updateNilReturn(zReference, taxYear, month, nilReturn = false).futureValue.value

        result.nilReturn mustBe false
        result.fileUploads mustBe Nil
        result.createdOn mustBe existingUpdated
        result.lastUpdated mustBe fixedNow
      }

      "must return None when the MonthlyReturn does not exist" in {
        repository.updateNilReturn(zReference, taxYear, month, nilReturn = true).futureValue mustBe None
      }
    }

    "createFileUpload" - {

      "must return None when the MonthlyReturn does not exist" in {
        repository
          .createFileUpload(zReference, taxYear, month, uploadReference)
          .futureValue mustBe MonthlyReturnNotFound

        repository.get(zReference, taxYear, month).futureValue mustBe None
      }

      "must append a CREATED file upload when the MonthlyReturn exists" in {
        repository
          .upsert(buildMonthlyReturn(fileUploads = List(createdFileUpload(reference = "existing-reference"))))
          .futureValue

        val result = repository.createFileUpload(zReference, taxYear, month, uploadReference).futureValue

        val stored = repository.get(zReference, taxYear, month).futureValue.value
        result mustBe FileUploadCreated(stored)
        stored.fileUploads mustBe List(
          createdFileUpload(reference = "existing-reference"),
          FileUpload(
            reference = uploadReference,
            status = Created,
            createdOn = fixedNow
          )
        )
        stored.lastUpdated mustBe fixedNow
      }

      "must not duplicate an existing file upload reference" in {
        repository.upsert(buildMonthlyReturn()).futureValue
        repository.createFileUpload(zReference, taxYear, month, uploadReference).futureValue
        repository
          .createFileUpload(zReference, taxYear, month, uploadReference)
          .futureValue mustBe FileUploadAlreadyExists

        val stored = repository.get(zReference, taxYear, month).futureValue.value
        stored.fileUploads.map(_.reference) mustBe List(uploadReference)
      }

      "must not append a file upload when the MonthlyReturn is a nil return" in {
        repository.upsert(buildMonthlyReturn(nilReturn = true)).futureValue

        repository
          .createFileUpload(zReference, taxYear, month, uploadReference)
          .futureValue mustBe MonthlyReturnNotFound

        repository.get(zReference, taxYear, month).futureValue.value.fileUploads mustBe Nil
      }
    }

    "getFileUpload" - {

      "must return a FileUpload when the MonthlyReturn and FileUpload exist" in {
        val fileUpload = createdFileUpload()
        repository.upsert(buildMonthlyReturn(fileUploads = List(fileUpload))).futureValue

        repository.getFileUpload(zReference, taxYear, month, uploadReference).futureValue mustBe Some(fileUpload)
      }

      "must return None when the MonthlyReturn does not exist" in {
        repository.getFileUpload(zReference, taxYear, month, uploadReference).futureValue mustBe None
      }

      "must return None when the FileUpload does not exist" in {
        repository.upsert(buildMonthlyReturn()).futureValue

        repository.getFileUpload(zReference, taxYear, month, uploadReference).futureValue mustBe None
      }
    }

    "completeUpscan" - {

      "must complete a successful file upload" in {
        repository.upsert(buildMonthlyReturn()).futureValue
        repository.createFileUpload(zReference, taxYear, month, uploadReference).futureValue

        repository
          .completeUpscan(
            zReference = zReference,
            taxYear = taxYear,
            month = month,
            reference = uploadReference,
            status = FileUploadStatus.UpscanSuccess,
            fileUploadDetails = Some(fileUploadDetails)
          )
          .futureValue mustBe true

        val fileUpload = repository.get(zReference, taxYear, month).futureValue.value.fileUploads.head
        fileUpload mustBe FileUpload(
          reference = uploadReference,
          status = FileUploadStatus.UpscanSuccess,
          createdOn = fixedNow,
          upscanCompletedOn = Some(fixedNow),
          fileUploadDetails = Some(fileUploadDetails)
        )
      }

      "must complete a failed file upload" in {
        repository.upsert(buildMonthlyReturn()).futureValue
        repository.createFileUpload(zReference, taxYear, month, uploadReference).futureValue

        repository
          .completeUpscan(
            zReference = zReference,
            taxYear = taxYear,
            month = month,
            reference = uploadReference,
            status = UpscanRejected,
            fileUploadDetails = None,
            failureReason = Some(Rejected),
            failureMessage = Some(testDuplicateFileMessage)
          )
          .futureValue mustBe true

        val fileUpload = repository.get(zReference, taxYear, month).futureValue.value.fileUploads.head
        fileUpload mustBe FileUpload(
          reference = uploadReference,
          status = UpscanRejected,
          createdOn = fixedNow,
          upscanCompletedOn = Some(fixedNow),
          failureReason = Some(Rejected),
          failureMessage = Some(testDuplicateFileMessage)
        )
      }

      "must return false when the MonthlyReturn does not exist" in {
        repository
          .completeUpscan(
            zReference = zReference,
            taxYear = taxYear,
            month = month,
            reference = uploadReference,
            status = FileUploadStatus.UpscanSuccess,
            fileUploadDetails = Some(fileUploadDetails)
          )
          .futureValue mustBe false

        repository.get(zReference, taxYear, month).futureValue mustBe None
      }

      "must add a completed file upload when the file upload reference does not exist" in {
        repository.upsert(buildMonthlyReturn()).futureValue
        repository.createFileUpload(zReference, taxYear, month, uploadReference).futureValue

        repository
          .completeUpscan(
            zReference = zReference,
            taxYear = taxYear,
            month = month,
            reference = missingUploadReference,
            status = FileUploadStatus.UpscanSuccess,
            fileUploadDetails = Some(fileUploadDetails)
          )
          .futureValue mustBe true

        val stored = repository.get(zReference, taxYear, month).futureValue.value
        stored.fileUploads mustBe List(
          FileUpload(
            reference = uploadReference,
            status = Created,
            createdOn = fixedNow
          ),
          FileUpload(
            reference = missingUploadReference,
            status = FileUploadStatus.UpscanSuccess,
            createdOn = fixedNow,
            upscanCompletedOn = Some(fixedNow),
            fileUploadDetails = Some(fileUploadDetails)
          )
        )
      }

      "must add a completed file upload when the MonthlyReturn exists without a CREATED upload" in {
        repository.upsert(buildMonthlyReturn()).futureValue

        repository
          .completeUpscan(
            zReference = zReference,
            taxYear = taxYear,
            month = month,
            reference = uploadReference,
            status = FileUploadStatus.UpscanSuccess,
            fileUploadDetails = Some(fileUploadDetails)
          )
          .futureValue mustBe true

        repository.get(zReference, taxYear, month).futureValue.value.fileUploads mustBe List(
          FileUpload(
            reference = uploadReference,
            status = FileUploadStatus.UpscanSuccess,
            createdOn = fixedNow,
            upscanCompletedOn = Some(fixedNow),
            fileUploadDetails = Some(fileUploadDetails)
          )
        )
      }

      "must return false when the MonthlyReturn is a nil return" in {
        repository.upsert(buildMonthlyReturn(nilReturn = true)).futureValue

        repository
          .completeUpscan(
            zReference = zReference,
            taxYear = taxYear,
            month = month,
            reference = uploadReference,
            status = FileUploadStatus.UpscanSuccess,
            fileUploadDetails = Some(fileUploadDetails)
          )
          .futureValue mustBe false

        repository.get(zReference, taxYear, month).futureValue.value.fileUploads mustBe Nil
      }
    }
  }

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

  private def createdFileUpload(
    reference: String = uploadReference
  ): FileUpload =
    FileUpload(
      reference = reference,
      status = Created,
      createdOn = createdOn
    )
}
