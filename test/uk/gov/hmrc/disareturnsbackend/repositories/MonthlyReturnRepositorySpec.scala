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
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import java.time.{Clock, Instant, ZoneOffset}

class MonthlyReturnRepositorySpec extends SpecBase with DefaultPlayMongoRepositorySupport[MonthlyReturn] {

  override protected def databaseName: String = "disa-returns-backend-monthly-return-repository-test"

  private val fixedNow: Instant = Instant.parse("2026-05-17T12:00:00Z")
  private val fixedClock: Clock = Clock.fixed(fixedNow, ZoneOffset.UTC)

  private lazy val appConfig: AppConfig = app.injector.instanceOf[AppConfig]

  override protected val repository: MonthlyReturnRepository =
    new MonthlyReturnRepository(mongoComponent, appConfig, fixedClock)

  override protected def afterAll(): Unit =
    try dropDatabase()
    finally super.afterAll()

  private val zReference        = "Z1234"
  private val taxYear           = "2026"
  private val month             = "5"
  private val uploadReference   = "2b4d6f3a-8c1e-4e4b-9c7a-123456789abc"
  private val existingUpdated   = Instant.parse("2026-05-17T11:00:00Z")
  private val createdOn         = Instant.parse("2026-05-17T11:30:00Z")
  private val downloadUrl       = "https://fus-outbound-bucket.s3.eu-west-2.amazonaws.com/object-key?X-Amz-Signature=abc"
  private val fileUploadDetails = FileUploadDetails(
    fileName = "return.csv",
    fileMimeType = "text/csv",
    uploadTimestamp = Instant.parse("2026-05-17T11:59:00Z"),
    checksum = "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
    size = 1024L
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

        repository.get(zReference, taxYear, "6").futureValue mustBe None
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

    "createFileUpload" - {

      "must create a MonthlyReturn when one does not exist" in {
        repository.createFileUpload(zReference, taxYear, month, uploadReference).futureValue mustBe true

        repository.get(zReference, taxYear, month).futureValue.value mustBe MonthlyReturn(
          zReference = zReference,
          taxYear = taxYear,
          month = month,
          fileUploads = List(
            FileUpload(
              reference = uploadReference,
              status = Created,
              createdOn = fixedNow
            )
          ),
          lastUpdated = fixedNow
        )
      }

      "must append a CREATED file upload when the MonthlyReturn exists" in {
        repository
          .upsert(buildMonthlyReturn(fileUploads = List(createdFileUpload(reference = "existing-reference"))))
          .futureValue

        repository.createFileUpload(zReference, taxYear, month, uploadReference).futureValue mustBe true

        val stored = repository.get(zReference, taxYear, month).futureValue.value
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
        repository.createFileUpload(zReference, taxYear, month, uploadReference).futureValue mustBe true
        repository.createFileUpload(zReference, taxYear, month, uploadReference).futureValue mustBe false

        val stored = repository.get(zReference, taxYear, month).futureValue.value
        stored.fileUploads.map(_.reference) mustBe List(uploadReference)
      }
    }

    "completeFileUpload" - {

      "must complete a successful file upload" in {
        repository.createFileUpload(zReference, taxYear, month, uploadReference).futureValue

        repository
          .completeFileUpload(
            zReference = zReference,
            taxYear = taxYear,
            month = month,
            reference = uploadReference,
            status = FileUploadStatus.UpscanSuccess,
            fileUploadDetails = Some(fileUploadDetails),
            downloadUrl = Some(downloadUrl)
          )
          .futureValue mustBe true

        val fileUpload = repository.get(zReference, taxYear, month).futureValue.value.fileUploads.head
        fileUpload mustBe FileUpload(
          reference = uploadReference,
          status = FileUploadStatus.UpscanSuccess,
          createdOn = fixedNow,
          completedOn = Some(fixedNow),
          fileUploadDetails = Some(fileUploadDetails),
          downloadUrl = Some(downloadUrl)
        )
      }

      "must complete a failed file upload" in {
        repository.createFileUpload(zReference, taxYear, month, uploadReference).futureValue

        repository
          .completeFileUpload(
            zReference = zReference,
            taxYear = taxYear,
            month = month,
            reference = uploadReference,
            status = UpscanRejected,
            fileUploadDetails = None,
            failureReason = Some(Rejected),
            failureMessage = Some("Duplicate file")
          )
          .futureValue mustBe true

        val fileUpload = repository.get(zReference, taxYear, month).futureValue.value.fileUploads.head
        fileUpload mustBe FileUpload(
          reference = uploadReference,
          status = UpscanRejected,
          createdOn = fixedNow,
          completedOn = Some(fixedNow),
          failureReason = Some(Rejected),
          failureMessage = Some("Duplicate file")
        )
      }

      "must return false when the MonthlyReturn does not exist" in {
        repository
          .completeFileUpload(
            zReference = zReference,
            taxYear = taxYear,
            month = month,
            reference = uploadReference,
            status = FileUploadStatus.UpscanSuccess,
            fileUploadDetails = Some(fileUploadDetails),
            downloadUrl = Some(downloadUrl)
          )
          .futureValue mustBe false

        repository.get(zReference, taxYear, month).futureValue mustBe None
      }

      "must return false when the file upload reference does not exist" in {
        repository.createFileUpload(zReference, taxYear, month, uploadReference).futureValue

        repository
          .completeFileUpload(
            zReference = zReference,
            taxYear = taxYear,
            month = month,
            reference = "missing-reference",
            status = FileUploadStatus.UpscanSuccess,
            fileUploadDetails = Some(fileUploadDetails),
            downloadUrl = Some(downloadUrl)
          )
          .futureValue mustBe false

        val stored = repository.get(zReference, taxYear, month).futureValue.value
        stored.fileUploads.map(_.reference) mustBe List(uploadReference)
        stored.fileUploads.head.status mustBe Created
      }
    }
  }

  private def buildMonthlyReturn(
    fileUploads: List[FileUpload] = Nil,
    lastUpdated: Instant = existingUpdated
  ): MonthlyReturn =
    MonthlyReturn(
      zReference = zReference,
      taxYear = taxYear,
      month = month,
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
