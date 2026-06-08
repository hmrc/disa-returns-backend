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
import org.mockito.ArgumentMatchers.eq as eqTo
import org.mockito.Mockito.{reset, verify, when}
import org.scalatest.BeforeAndAfterEach
import uk.gov.hmrc.disareturnsbackend.models.*
import uk.gov.hmrc.disareturnsbackend.repositories.MonthlyReturnRepository
import uk.gov.hmrc.disareturnsbackend.repositories.MonthlyReturnRepository.CreateFileUploadRepositoryResult
import uk.gov.hmrc.disareturnsbackend.services.CreateMonthlyReturnResult.{AlreadyExists, Created}

import scala.concurrent.Future

class MonthlyReturnServiceSpec extends SpecBase with BeforeAndAfterEach {

  private val mockMonthlyReturnRepository = mock[MonthlyReturnRepository]
  private val mockUuidGenerator           = mock[UuidGenerator]
  private val service                     = new MonthlyReturnService(mockMonthlyReturnRepository, mockUuidGenerator)

  private val zReference      = testZReference
  private val taxYear         = testTaxYear
  private val month           = testMonth
  private val uploadReference = testUploadReference

  private val monthlyReturn = MonthlyReturn(
    zReference = zReference,
    submissionId = testSubmissionId,
    taxYear = taxYear,
    month = month,
    createdOn = testExistingUpdatedOn,
    nilReturn = false,
    fileUploads = Nil,
    lastUpdated = testCreatedOn
  )

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockMonthlyReturnRepository)
    reset(mockUuidGenerator)
  }

  "MonthlyReturnService" - {

    "get" - {

      "must return the repository result" in {
        when(mockMonthlyReturnRepository.get(eqTo(zReference), eqTo(taxYear), eqTo(month)))
          .thenReturn(Future.successful(Some(monthlyReturn)))

        service.get(zReference, taxYear, month).futureValue mustBe Some(monthlyReturn)
      }
    }

    "create" - {

      "must return Created when the repository creates the MonthlyReturn" in {
        when(mockUuidGenerator.randomUuid()).thenReturn(testSubmissionId)
        when(
          mockMonthlyReturnRepository.create(
            eqTo(zReference),
            eqTo(taxYear),
            eqTo(month),
            eqTo(testSubmissionId),
            eqTo(true)
          )
        )
          .thenReturn(Future.successful(Some(monthlyReturn)))

        service.create(zReference, taxYear, month, nilReturn = true).futureValue mustBe Created(testSubmissionId)
      }

      "must return AlreadyExists when the repository rejects the create" in {
        when(mockUuidGenerator.randomUuid()).thenReturn(testSubmissionId)
        when(
          mockMonthlyReturnRepository.create(
            eqTo(zReference),
            eqTo(taxYear),
            eqTo(month),
            eqTo(testSubmissionId),
            eqTo(false)
          )
        )
          .thenReturn(Future.successful(None))

        service.create(zReference, taxYear, month, nilReturn = false).futureValue mustBe AlreadyExists
      }
    }

    "updateNilReturn" - {

      "must return the repository result" in {
        val updatedReturn = monthlyReturn.copy(nilReturn = true)

        when(mockMonthlyReturnRepository.updateNilReturn(eqTo(zReference), eqTo(taxYear), eqTo(month), eqTo(true)))
          .thenReturn(Future.successful(Some(updatedReturn)))

        service.updateNilReturn(zReference, taxYear, month, nilReturn = true).futureValue mustBe Some(updatedReturn)

        verify(mockMonthlyReturnRepository).updateNilReturn(eqTo(zReference), eqTo(taxYear), eqTo(month), eqTo(true))
      }
    }

    "createFileUpload" - {

      "must return the repository result" in {
        val updatedReturn = monthlyReturn.copy(
          fileUploads = List(
            FileUpload(
              reference = uploadReference,
              status = FileUploadStatus.Created,
              createdOn = testCreatedOn
            )
          )
        )

        when(
          mockMonthlyReturnRepository
            .createFileUpload(eqTo(zReference), eqTo(taxYear), eqTo(month), eqTo(uploadReference))
        )
          .thenReturn(Future.successful(CreateFileUploadRepositoryResult.FileUploadCreated(updatedReturn)))

        service.createFileUpload(zReference, taxYear, month, uploadReference).futureValue mustBe
          CreateFileUploadResult.FileUploadCreated(updatedReturn)

        verify(mockMonthlyReturnRepository).createFileUpload(
          eqTo(zReference),
          eqTo(taxYear),
          eqTo(month),
          eqTo(uploadReference)
        )
      }

      "must return FileUploadAlreadyExists when the repository rejects a duplicate reference" in {
        when(
          mockMonthlyReturnRepository
            .createFileUpload(eqTo(zReference), eqTo(taxYear), eqTo(month), eqTo(uploadReference))
        )
          .thenReturn(Future.successful(CreateFileUploadRepositoryResult.FileUploadAlreadyExists))

        service.createFileUpload(zReference, taxYear, month, uploadReference).futureValue mustBe
          CreateFileUploadResult.FileUploadAlreadyExists
      }
    }

    "getFileUpload" - {

      "must return the repository result" in {
        val fileUpload = FileUpload(
          reference = uploadReference,
          status = FileUploadStatus.Created,
          createdOn = testCreatedOn
        )

        when(
          mockMonthlyReturnRepository.getFileUpload(eqTo(zReference), eqTo(taxYear), eqTo(month), eqTo(uploadReference))
        )
          .thenReturn(Future.successful(Some(fileUpload)))

        service.getFileUpload(zReference, taxYear, month, uploadReference).futureValue mustBe Some(fileUpload)

        verify(mockMonthlyReturnRepository).getFileUpload(
          eqTo(zReference),
          eqTo(taxYear),
          eqTo(month),
          eqTo(uploadReference)
        )
      }
    }
  }
}
