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
import org.mockito.Mockito.{reset, verify, verifyNoInteractions, when}
import org.scalatest.BeforeAndAfterEach
import uk.gov.hmrc.disareturnsbackend.connectors.ReturnsSubmissionConnector
import uk.gov.hmrc.disareturnsbackend.connectors.ReturnsSubmissionConnector.CreateMonthlyReturnSubmissionResult
import uk.gov.hmrc.disareturnsbackend.config.AppConfig
import uk.gov.hmrc.disareturnsbackend.models.*
import uk.gov.hmrc.disareturnsbackend.repositories.MonthlyReturnRepository
import uk.gov.hmrc.disareturnsbackend.repositories.MonthlyReturnRepository.{CreateFileUploadRepositoryResult, DeclareMonthlyReturnRepositoryResult, UpdateNilReturnRepositoryResult}
import uk.gov.hmrc.disareturnsbackend.services.CreateMonthlyReturnResult.{AlreadyExists, Created}

import java.time.{Clock, Instant, LocalDate, ZoneOffset}
import scala.concurrent.Future

class MonthlyReturnServiceSpec extends SpecBase with BeforeAndAfterEach {

  private val mockMonthlyReturnRepository    = mock[MonthlyReturnRepository]
  private val mockReturnsSubmissionConnector = mock[ReturnsSubmissionConnector]
  private val appConfig                      = inject[AppConfig]
  private val service                        = buildService(testCreatedOn)

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
    reset(mockReturnsSubmissionConnector)
  }

  "MonthlyReturnService" - {

    "get" - {

      "must return the repository result" in {
        when(mockMonthlyReturnRepository.get(eqTo(zReference), eqTo(taxYear), eqTo(month)))
          .thenReturn(Future.successful(Some(monthlyReturn)))

        service.get(zReference, taxYear, month).futureValue mustBe Some(monthlyReturn)
      }

      "must fail when the repository fails" in {
        val exception = new RuntimeException(testMongoDownMessage)

        when(mockMonthlyReturnRepository.get(eqTo(zReference), eqTo(taxYear), eqTo(month)))
          .thenReturn(Future.failed(exception))

        service.get(zReference, taxYear, month).failed.futureValue mustBe exception
      }
    }

    "create" - {

      "must return Created when submission and backend create the MonthlyReturn" in {
        when(mockMonthlyReturnRepository.get(eqTo(zReference), eqTo(taxYear), eqTo(month)))
          .thenReturn(Future.successful(None))
        when(
          mockReturnsSubmissionConnector.createMonthlyReturn(eqTo(zReference), eqTo(taxYear), eqTo(month), eqTo(true))(
            any(),
            any()
          )
        )
          .thenReturn(Future.successful(CreateMonthlyReturnSubmissionResult.Created(testSubmissionId)))
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

      "must create backend and return Created when submission already has the MonthlyReturn" in {
        when(mockMonthlyReturnRepository.get(eqTo(zReference), eqTo(taxYear), eqTo(month)))
          .thenReturn(Future.successful(None))
        when(
          mockReturnsSubmissionConnector.createMonthlyReturn(eqTo(zReference), eqTo(taxYear), eqTo(month), eqTo(false))(
            any(),
            any()
          )
        )
          .thenReturn(Future.successful(CreateMonthlyReturnSubmissionResult.AlreadyExists(testSubmissionId)))
        when(
          mockMonthlyReturnRepository.create(
            eqTo(zReference),
            eqTo(taxYear),
            eqTo(month),
            eqTo(testSubmissionId),
            eqTo(false)
          )
        )
          .thenReturn(Future.successful(Some(monthlyReturn)))

        service.create(zReference, taxYear, month, nilReturn = false).futureValue mustBe Created(testSubmissionId)
      }

      "must return AlreadyExists without calling submission when backend already has the MonthlyReturn" in {
        when(mockMonthlyReturnRepository.get(eqTo(zReference), eqTo(taxYear), eqTo(month)))
          .thenReturn(Future.successful(Some(monthlyReturn)))

        service.create(zReference, taxYear, month, nilReturn = false).futureValue mustBe AlreadyExists

        verifyNoInteractions(mockReturnsSubmissionConnector)
      }

      "must return AlreadyExists when the backend repository rejects the create" in {
        when(mockMonthlyReturnRepository.get(eqTo(zReference), eqTo(taxYear), eqTo(month)))
          .thenReturn(Future.successful(None))
        when(
          mockReturnsSubmissionConnector.createMonthlyReturn(eqTo(zReference), eqTo(taxYear), eqTo(month), eqTo(false))(
            any(),
            any()
          )
        )
          .thenReturn(Future.successful(CreateMonthlyReturnSubmissionResult.Created(testSubmissionId)))
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

      "must fail when the repository get fails" in {
        val exception = new RuntimeException(testMongoDownMessage)

        when(mockMonthlyReturnRepository.get(eqTo(zReference), eqTo(taxYear), eqTo(month)))
          .thenReturn(Future.failed(exception))

        service.create(zReference, taxYear, month, nilReturn = false).failed.futureValue mustBe exception
      }

      "must fail when the backend repository create fails" in {
        val exception = new RuntimeException(testMongoDownMessage)

        when(mockMonthlyReturnRepository.get(eqTo(zReference), eqTo(taxYear), eqTo(month)))
          .thenReturn(Future.successful(None))
        when(
          mockReturnsSubmissionConnector.createMonthlyReturn(eqTo(zReference), eqTo(taxYear), eqTo(month), eqTo(false))(
            any(),
            any()
          )
        )
          .thenReturn(Future.successful(CreateMonthlyReturnSubmissionResult.Created(testSubmissionId)))
        when(
          mockMonthlyReturnRepository.create(
            eqTo(zReference),
            eqTo(taxYear),
            eqTo(month),
            eqTo(testSubmissionId),
            eqTo(false)
          )
        )
          .thenReturn(Future.failed(exception))

        service.create(zReference, taxYear, month, nilReturn = false).failed.futureValue mustBe exception
      }

      "must fail when submission create fails" in {
        val exception = new RuntimeException(testMongoDownMessage)

        when(mockMonthlyReturnRepository.get(eqTo(zReference), eqTo(taxYear), eqTo(month)))
          .thenReturn(Future.successful(None))
        when(
          mockReturnsSubmissionConnector.createMonthlyReturn(eqTo(zReference), eqTo(taxYear), eqTo(month), eqTo(false))(
            any(),
            any()
          )
        )
          .thenReturn(Future.failed(exception))

        service.create(zReference, taxYear, month, nilReturn = false).failed.futureValue mustBe exception
      }
    }

    "updateNilReturn" - {

      "must return the repository result" in {
        val updatedReturn = monthlyReturn.copy(nilReturn = true)

        when(mockMonthlyReturnRepository.updateNilReturn(eqTo(zReference), eqTo(taxYear), eqTo(month), eqTo(true)))
          .thenReturn(Future.successful(UpdateNilReturnRepositoryResult.NilReturnUpdated(updatedReturn)))

        service.updateNilReturn(zReference, taxYear, month, nilReturn = true).futureValue mustBe
          UpdateNilReturnResult.NilReturnUpdated(updatedReturn)

        verify(mockMonthlyReturnRepository).updateNilReturn(eqTo(zReference), eqTo(taxYear), eqTo(month), eqTo(true))
      }

      "must return MonthlyReturnAlreadyDeclared when the repository rejects a declared return" in {
        when(mockMonthlyReturnRepository.updateNilReturn(eqTo(zReference), eqTo(taxYear), eqTo(month), eqTo(true)))
          .thenReturn(Future.successful(UpdateNilReturnRepositoryResult.MonthlyReturnAlreadyDeclared))

        service.updateNilReturn(zReference, taxYear, month, nilReturn = true).futureValue mustBe
          UpdateNilReturnResult.MonthlyReturnAlreadyDeclared
      }

      "must return MonthlyReturnNotFound when the repository cannot find the MonthlyReturn" in {
        when(mockMonthlyReturnRepository.updateNilReturn(eqTo(zReference), eqTo(taxYear), eqTo(month), eqTo(true)))
          .thenReturn(Future.successful(UpdateNilReturnRepositoryResult.MonthlyReturnNotFound))

        service.updateNilReturn(zReference, taxYear, month, nilReturn = true).futureValue mustBe
          UpdateNilReturnResult.MonthlyReturnNotFound
      }

      "must fail when the repository fails" in {
        val exception = new RuntimeException(testMongoDownMessage)

        when(mockMonthlyReturnRepository.updateNilReturn(eqTo(zReference), eqTo(taxYear), eqTo(month), eqTo(true)))
          .thenReturn(Future.failed(exception))

        service.updateNilReturn(zReference, taxYear, month, nilReturn = true).failed.futureValue mustBe exception
      }
    }

    "declare" - {

      "must return Declared when the repository declares the MonthlyReturn" in {
        when(mockMonthlyReturnRepository.declare(eqTo(zReference), eqTo(taxYear), eqTo(month)))
          .thenReturn(
            Future.successful(DeclareMonthlyReturnRepositoryResult.MonthlyReturnDeclared)
          )

        service.declare(zReference, taxYear, month).futureValue mustBe DeclareMonthlyReturnResult.Declared

        verify(mockMonthlyReturnRepository).declare(eqTo(zReference), eqTo(taxYear), eqTo(month))
      }

      "must return AlreadyDeclared when the repository rejects a duplicate declaration" in {
        when(mockMonthlyReturnRepository.declare(eqTo(zReference), eqTo(taxYear), eqTo(month)))
          .thenReturn(Future.successful(DeclareMonthlyReturnRepositoryResult.MonthlyReturnAlreadyDeclared))

        service.declare(zReference, taxYear, month).futureValue mustBe DeclareMonthlyReturnResult.AlreadyDeclared
      }

      "must return MonthlyReturnNotFound when the repository cannot find the MonthlyReturn" in {
        when(mockMonthlyReturnRepository.declare(eqTo(zReference), eqTo(taxYear), eqTo(month)))
          .thenReturn(Future.successful(DeclareMonthlyReturnRepositoryResult.MonthlyReturnNotFound))

        service.declare(zReference, taxYear, month).futureValue mustBe DeclareMonthlyReturnResult.MonthlyReturnNotFound
      }

      "must allow declarations from the configured start day" in {
        val startDayService = buildService(declarationPeriodStartsAt)
        when(mockMonthlyReturnRepository.declare(eqTo(zReference), eqTo(taxYear), eqTo(month)))
          .thenReturn(
            Future.successful(DeclareMonthlyReturnRepositoryResult.MonthlyReturnDeclared)
          )

        startDayService.declare(zReference, taxYear, month).futureValue mustBe
          DeclareMonthlyReturnResult.Declared
      }

      "must return OutsideDeclarationPeriod and not call the repository before the configured start day" in {
        val beforeStartDayService = buildService(
          now = Instant.parse("2026-05-05T23:59:59Z"),
          serviceAppConfig = declarationPeriodAppConfig(startDay = 6, endDay = 19)
        )

        beforeStartDayService.declare(zReference, taxYear, month).futureValue mustBe
          DeclareMonthlyReturnResult.OutsideDeclarationPeriod

        verifyNoInteractions(mockMonthlyReturnRepository)
      }

      "must allow declarations until the end of the configured end day" in {
        val endDayService = buildService(declarationPeriodEndsAt)
        when(mockMonthlyReturnRepository.declare(eqTo(zReference), eqTo(taxYear), eqTo(month)))
          .thenReturn(
            Future.successful(DeclareMonthlyReturnRepositoryResult.MonthlyReturnDeclared)
          )

        endDayService.declare(zReference, taxYear, month).futureValue mustBe
          DeclareMonthlyReturnResult.Declared
      }

      "must return OutsideDeclarationPeriod and not call the repository after the configured end day" in {
        val afterEndDayService = buildService(
          now = Instant.parse("2026-05-20T00:00:00Z"),
          serviceAppConfig = declarationPeriodAppConfig(startDay = 6, endDay = 19)
        )

        afterEndDayService.declare(zReference, taxYear, month).futureValue mustBe
          DeclareMonthlyReturnResult.OutsideDeclarationPeriod

        verifyNoInteractions(mockMonthlyReturnRepository)
      }

      "must fail when the repository fails" in {
        val exception = new RuntimeException(testMongoDownMessage)

        when(mockMonthlyReturnRepository.declare(eqTo(zReference), eqTo(taxYear), eqTo(month)))
          .thenReturn(Future.failed(exception))

        service.declare(zReference, taxYear, month).failed.futureValue mustBe exception
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

      "must return MonthlyReturnAlreadyDeclared when the repository rejects a declared return" in {
        when(
          mockMonthlyReturnRepository
            .createFileUpload(eqTo(zReference), eqTo(taxYear), eqTo(month), eqTo(uploadReference))
        )
          .thenReturn(Future.successful(CreateFileUploadRepositoryResult.MonthlyReturnAlreadyDeclared))

        service.createFileUpload(zReference, taxYear, month, uploadReference).futureValue mustBe
          CreateFileUploadResult.MonthlyReturnAlreadyDeclared
      }

      "must fail when the repository fails" in {
        val exception = new RuntimeException(testMongoDownMessage)

        when(
          mockMonthlyReturnRepository
            .createFileUpload(eqTo(zReference), eqTo(taxYear), eqTo(month), eqTo(uploadReference))
        )
          .thenReturn(Future.failed(exception))

        service.createFileUpload(zReference, taxYear, month, uploadReference).failed.futureValue mustBe exception
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

      "must fail when the repository fails" in {
        val exception = new RuntimeException(testMongoDownMessage)

        when(
          mockMonthlyReturnRepository.getFileUpload(eqTo(zReference), eqTo(taxYear), eqTo(month), eqTo(uploadReference))
        )
          .thenReturn(Future.failed(exception))

        service.getFileUpload(zReference, taxYear, month, uploadReference).failed.futureValue mustBe exception
      }
    }

    "deleteFileUpload" - {

      "must return the repository result" in {
        when(
          mockMonthlyReturnRepository
            .deleteFileUpload(eqTo(zReference), eqTo(taxYear), eqTo(month), eqTo(uploadReference))
        )
          .thenReturn(Future.successful(true))

        service.deleteFileUpload(zReference, taxYear, month, uploadReference).futureValue mustBe true

        verify(mockMonthlyReturnRepository).deleteFileUpload(
          eqTo(zReference),
          eqTo(taxYear),
          eqTo(month),
          eqTo(uploadReference)
        )
      }

      "must return false when the repository cannot delete a file upload" in {
        when(
          mockMonthlyReturnRepository
            .deleteFileUpload(eqTo(zReference), eqTo(taxYear), eqTo(month), eqTo(uploadReference))
        )
          .thenReturn(Future.successful(false))

        service.deleteFileUpload(zReference, taxYear, month, uploadReference).futureValue mustBe false
      }

      "must fail when the repository fails" in {
        val exception = new RuntimeException(testMongoDownMessage)

        when(
          mockMonthlyReturnRepository
            .deleteFileUpload(eqTo(zReference), eqTo(taxYear), eqTo(month), eqTo(uploadReference))
        )
          .thenReturn(Future.failed(exception))

        service.deleteFileUpload(zReference, taxYear, month, uploadReference).failed.futureValue mustBe exception
      }
    }
  }

  private def buildService(now: Instant, serviceAppConfig: AppConfig = appConfig): MonthlyReturnService =
    new MonthlyReturnService(
      monthlyReturnRepository = mockMonthlyReturnRepository,
      returnsSubmissionConnector = mockReturnsSubmissionConnector,
      appConfig = serviceAppConfig,
      clock = Clock.fixed(now, ZoneOffset.UTC)
    )

  private def declarationPeriodAppConfig(startDay: Int, endDay: Int): AppConfig = {
    val configuredAppConfig = mock[AppConfig]
    when(configuredAppConfig.declarationPeriodStart).thenReturn(startDay)
    when(configuredAppConfig.declarationPeriodEnd).thenReturn(endDay)
    configuredAppConfig
  }

  private def declarationPeriodStartsAt: Instant =
    LocalDate
      .of(2026, 5, appConfig.declarationPeriodStart)
      .atStartOfDay(ZoneOffset.UTC)
      .toInstant

  private def declarationPeriodEndsAt: Instant =
    LocalDate
      .of(2026, 5, appConfig.declarationPeriodEnd)
      .atTime(23, 59, 59)
      .toInstant(ZoneOffset.UTC)
}
