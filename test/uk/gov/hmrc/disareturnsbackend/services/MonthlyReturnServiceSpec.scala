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
import uk.gov.hmrc.disareturnsbackend.connectors.ReturnsSubmissionConnector.{CreateMonthlyReturnSubmissionResult, DeclareMonthlyReturnSubmissionResult}
import uk.gov.hmrc.disareturnsbackend.models.*
import uk.gov.hmrc.disareturnsbackend.repositories.{CreateFileUploadRepositoryResult, MonthlyReturnRepository, UpdateNilReturnRepositoryResult}
import uk.gov.hmrc.disareturnsbackend.services.CreateMonthlyReturnResult.{AlreadyExists, Created}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class MonthlyReturnServiceSpec extends SpecBase with BeforeAndAfterEach {

  private val mockMonthlyReturnRepository    = mock[MonthlyReturnRepository]
  private val mockReturnsSubmissionConnector = mock[ReturnsSubmissionConnector]
  private val service                        = buildService()

  private implicit val hc: HeaderCarrier = HeaderCarrier()

  private val zReference       = testZReference
  private val taxYear          = testTaxYear
  private val month            = testMonth
  private val uploadReference  = testUploadReference
  private val rowsValidated    = 1
  private val validationErrors = 0

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
    when(mockReturnsSubmissionConnector.isReportingWindowOpen(eqTo(zReference))(any(), any()))
      .thenReturn(Future.successful(true))
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

      "must update the MonthlyReturn and persist it" in {
        val expectedUpdatedReturn = monthlyReturn.copy(
          nilReturn = true,
          fileUploads = Nil,
          lastUpdated = testCreatedOn
        )

        when(mockMonthlyReturnRepository.updateNilReturn(eqTo(zReference), eqTo(taxYear), eqTo(month), eqTo(true)))
          .thenReturn(Future.successful(UpdateNilReturnRepositoryResult.NilReturnUpdated(expectedUpdatedReturn)))

        service.updateNilReturn(zReference, taxYear, month, nilReturn = true).futureValue mustBe
          UpdateNilReturnResult.NilReturnUpdated(expectedUpdatedReturn)

        verify(mockMonthlyReturnRepository).updateNilReturn(eqTo(zReference), eqTo(taxYear), eqTo(month), eqTo(true))
      }

      "must not persist when nilReturn is unchanged" in {
        when(mockMonthlyReturnRepository.updateNilReturn(eqTo(zReference), eqTo(taxYear), eqTo(month), eqTo(false)))
          .thenReturn(Future.successful(UpdateNilReturnRepositoryResult.NilReturnUpdated(monthlyReturn)))

        service.updateNilReturn(zReference, taxYear, month, nilReturn = false).futureValue mustBe
          UpdateNilReturnResult.NilReturnUpdated(monthlyReturn)

        verify(mockMonthlyReturnRepository).updateNilReturn(eqTo(zReference), eqTo(taxYear), eqTo(month), eqTo(false))
      }

      "must return MonthlyReturnNotFound when the repository cannot find the MonthlyReturn" in {
        when(mockMonthlyReturnRepository.updateNilReturn(eqTo(zReference), eqTo(taxYear), eqTo(month), eqTo(true)))
          .thenReturn(Future.successful(UpdateNilReturnRepositoryResult.MonthlyReturnNotFound))

        service.updateNilReturn(zReference, taxYear, month, nilReturn = true).futureValue mustBe
          UpdateNilReturnResult.MonthlyReturnNotFound
      }

      "must fail when the repository update fails" in {
        val exception = new RuntimeException(testMongoDownMessage)

        when(mockMonthlyReturnRepository.updateNilReturn(eqTo(zReference), eqTo(taxYear), eqTo(month), eqTo(true)))
          .thenReturn(Future.failed(exception))

        service.updateNilReturn(zReference, taxYear, month, nilReturn = true).failed.futureValue mustBe exception
      }
    }

    "declare" - {

      "must return Declared when submission declares the MonthlyReturn" in {
        when(mockMonthlyReturnRepository.get(eqTo(zReference), eqTo(taxYear), eqTo(month)))
          .thenReturn(Future.successful(Some(monthlyReturn)))
        when(
          mockReturnsSubmissionConnector
            .declareMonthlyReturn(eqTo(zReference), eqTo(taxYear), eqTo(month), any())(any(), any())
        )
          .thenReturn(Future.successful(DeclareMonthlyReturnSubmissionResult.Declared))

        service.declare(zReference, taxYear, month).futureValue mustBe DeclareMonthlyReturnResult.Declared

        verify(mockReturnsSubmissionConnector)
          .declareMonthlyReturn(eqTo(zReference), eqTo(taxYear), eqTo(month), any())(any(), any())
      }

      "must return AlreadyDeclared when submission rejects a duplicate declaration" in {
        when(mockMonthlyReturnRepository.get(eqTo(zReference), eqTo(taxYear), eqTo(month)))
          .thenReturn(Future.successful(Some(monthlyReturn)))
        when(
          mockReturnsSubmissionConnector
            .declareMonthlyReturn(eqTo(zReference), eqTo(taxYear), eqTo(month), any())(any(), any())
        )
          .thenReturn(Future.successful(DeclareMonthlyReturnSubmissionResult.AlreadyDeclared))

        service.declare(zReference, taxYear, month).futureValue mustBe DeclareMonthlyReturnResult.AlreadyDeclared
      }

      "must return OutsideDeclarationPeriod when submission reports that the declaration period is closed" in {
        when(mockMonthlyReturnRepository.get(eqTo(zReference), eqTo(taxYear), eqTo(month)))
          .thenReturn(Future.successful(Some(monthlyReturn)))
        when(
          mockReturnsSubmissionConnector
            .declareMonthlyReturn(eqTo(zReference), eqTo(taxYear), eqTo(month), any())(any(), any())
        )
          .thenReturn(Future.successful(DeclareMonthlyReturnSubmissionResult.OutsideDeclarationPeriod))

        service.declare(zReference, taxYear, month).futureValue mustBe
          DeclareMonthlyReturnResult.OutsideDeclarationPeriod
      }

      "must return MonthlyReturnNotFound when the repository cannot find the MonthlyReturn" in {
        when(mockMonthlyReturnRepository.get(eqTo(zReference), eqTo(taxYear), eqTo(month)))
          .thenReturn(Future.successful(None))

        service.declare(zReference, taxYear, month).futureValue mustBe DeclareMonthlyReturnResult.MonthlyReturnNotFound
      }

      "must return MonthlyReturnNotFound when submission cannot find the MonthlyReturn" in {
        when(mockMonthlyReturnRepository.get(eqTo(zReference), eqTo(taxYear), eqTo(month)))
          .thenReturn(Future.successful(Some(monthlyReturn)))
        when(
          mockReturnsSubmissionConnector
            .declareMonthlyReturn(eqTo(zReference), eqTo(taxYear), eqTo(month), any())(any(), any())
        )
          .thenReturn(Future.successful(DeclareMonthlyReturnSubmissionResult.MonthlyReturnNotFound))

        service.declare(zReference, taxYear, month).futureValue mustBe DeclareMonthlyReturnResult.MonthlyReturnNotFound
      }

      "must return OutsideDeclarationPeriod when submission reports that the window is closed" in {
        when(mockReturnsSubmissionConnector.isReportingWindowOpen(eqTo(zReference))(any(), any()))
          .thenReturn(Future.successful(false))

        service.declare(zReference, taxYear, month).futureValue mustBe
          DeclareMonthlyReturnResult.OutsideDeclarationPeriod

        verifyNoInteractions(mockMonthlyReturnRepository)
      }

      "must fail when the reporting window status check fails" in {
        val exception = new RuntimeException(testMongoDownMessage)
        when(mockReturnsSubmissionConnector.isReportingWindowOpen(eqTo(zReference))(any(), any()))
          .thenReturn(Future.failed(exception))

        service.declare(zReference, taxYear, month).failed.futureValue mustBe exception

        verifyNoInteractions(mockMonthlyReturnRepository)
      }

      "must fail when the repository fails" in {
        val exception = new RuntimeException(testMongoDownMessage)

        when(mockMonthlyReturnRepository.get(eqTo(zReference), eqTo(taxYear), eqTo(month)))
          .thenReturn(Future.failed(exception))

        service.declare(zReference, taxYear, month).failed.futureValue mustBe exception
      }

      "must fail when submission declare fails" in {
        val exception = new RuntimeException(testMongoDownMessage)

        when(mockMonthlyReturnRepository.get(eqTo(zReference), eqTo(taxYear), eqTo(month)))
          .thenReturn(Future.successful(Some(monthlyReturn)))
        when(
          mockReturnsSubmissionConnector
            .declareMonthlyReturn(eqTo(zReference), eqTo(taxYear), eqTo(month), any())(any(), any())
        )
          .thenReturn(Future.failed(exception))

        service.declare(zReference, taxYear, month).failed.futureValue mustBe exception
      }
    }

    "createFileUpload" - {

      "must create a file upload and persist it" in {
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

        verify(mockMonthlyReturnRepository)
          .createFileUpload(eqTo(zReference), eqTo(taxYear), eqTo(month), eqTo(uploadReference))
      }

      "must return FileUploadAlreadyExists when the reference already exists" in {
        when(
          mockMonthlyReturnRepository
            .createFileUpload(eqTo(zReference), eqTo(taxYear), eqTo(month), eqTo(uploadReference))
        )
          .thenReturn(Future.successful(CreateFileUploadRepositoryResult.FileUploadAlreadyExists))

        service.createFileUpload(zReference, taxYear, month, uploadReference).futureValue mustBe
          CreateFileUploadResult.FileUploadAlreadyExists

        verify(mockMonthlyReturnRepository)
          .createFileUpload(eqTo(zReference), eqTo(taxYear), eqTo(month), eqTo(uploadReference))
      }

      "must return MonthlyReturnNotFound when the MonthlyReturn is a nil return" in {
        when(
          mockMonthlyReturnRepository
            .createFileUpload(eqTo(zReference), eqTo(taxYear), eqTo(month), eqTo(uploadReference))
        )
          .thenReturn(Future.successful(CreateFileUploadRepositoryResult.MonthlyReturnNotFound))

        service.createFileUpload(zReference, taxYear, month, uploadReference).futureValue mustBe
          CreateFileUploadResult.MonthlyReturnNotFound

        verify(mockMonthlyReturnRepository)
          .createFileUpload(eqTo(zReference), eqTo(taxYear), eqTo(month), eqTo(uploadReference))
      }

      "must return MonthlyReturnNotFound when the MonthlyReturn does not exist" in {
        when(
          mockMonthlyReturnRepository
            .createFileUpload(eqTo(zReference), eqTo(taxYear), eqTo(month), eqTo(uploadReference))
        )
          .thenReturn(Future.successful(CreateFileUploadRepositoryResult.MonthlyReturnNotFound))

        service.createFileUpload(zReference, taxYear, month, uploadReference).futureValue mustBe
          CreateFileUploadResult.MonthlyReturnNotFound
      }

      "must fail when the repository create fails" in {
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

      "must return the file upload from the MonthlyReturn" in {
        val fileUpload = createdFileUpload()

        when(mockMonthlyReturnRepository.get(eqTo(zReference), eqTo(taxYear), eqTo(month)))
          .thenReturn(Future.successful(Some(monthlyReturn.copy(fileUploads = List(fileUpload)))))

        service.getFileUpload(zReference, taxYear, month, uploadReference).futureValue mustBe Some(fileUpload)
      }

      "must return None when the MonthlyReturn does not exist" in {
        when(mockMonthlyReturnRepository.get(eqTo(zReference), eqTo(taxYear), eqTo(month)))
          .thenReturn(Future.successful(None))

        service.getFileUpload(zReference, taxYear, month, uploadReference).futureValue mustBe None
      }

      "must return None when the file upload does not exist" in {
        when(mockMonthlyReturnRepository.get(eqTo(zReference), eqTo(taxYear), eqTo(month)))
          .thenReturn(Future.successful(Some(monthlyReturn)))

        service.getFileUpload(zReference, taxYear, month, uploadReference).futureValue mustBe None
      }

      "must fail when the repository fails" in {
        val exception = new RuntimeException(testMongoDownMessage)

        when(mockMonthlyReturnRepository.get(eqTo(zReference), eqTo(taxYear), eqTo(month)))
          .thenReturn(Future.failed(exception))

        service.getFileUpload(zReference, taxYear, month, uploadReference).failed.futureValue mustBe exception
      }
    }

    "deleteFileUpload" - {

      "must delete the file upload and persist the MonthlyReturn" in {
        when(
          mockMonthlyReturnRepository
            .deleteFileUpload(eqTo(zReference), eqTo(taxYear), eqTo(month), eqTo(uploadReference))
        )
          .thenReturn(Future.successful(true))

        service.deleteFileUpload(zReference, taxYear, month, uploadReference).futureValue mustBe true

        verify(mockMonthlyReturnRepository)
          .deleteFileUpload(eqTo(zReference), eqTo(taxYear), eqTo(month), eqTo(uploadReference))
      }

      "must return false when the file upload does not exist" in {
        when(
          mockMonthlyReturnRepository
            .deleteFileUpload(eqTo(zReference), eqTo(taxYear), eqTo(month), eqTo(uploadReference))
        )
          .thenReturn(Future.successful(false))

        service.deleteFileUpload(zReference, taxYear, month, uploadReference).futureValue mustBe false
      }

      "must return false when the MonthlyReturn does not exist" in {
        when(
          mockMonthlyReturnRepository
            .deleteFileUpload(eqTo(zReference), eqTo(taxYear), eqTo(month), eqTo(uploadReference))
        )
          .thenReturn(Future.successful(false))

        service.deleteFileUpload(zReference, taxYear, month, uploadReference).futureValue mustBe false
      }

      "must fail when the repository delete fails" in {
        val exception = new RuntimeException(testMongoDownMessage)

        when(
          mockMonthlyReturnRepository
            .deleteFileUpload(eqTo(zReference), eqTo(taxYear), eqTo(month), eqTo(uploadReference))
        )
          .thenReturn(Future.failed(exception))

        service.deleteFileUpload(zReference, taxYear, month, uploadReference).failed.futureValue mustBe exception
      }
    }

    "updateFileUploadProcessingDetails" - {

      "must update processing details and persist the MonthlyReturn" in {
        val validation       =
          FileUploadValidationResult(rowsValidated, validationErrors, FileUploadValidationStatus.ValidationSuccess)
        val returnWithUpload = monthlyReturn.copy(fileUploads = List(completedFileUpload()))
        when(
          mockMonthlyReturnRepository.updateFileUploadProcessingDetails(
            eqTo(zReference),
            eqTo(taxYear),
            eqTo(month),
            eqTo(uploadReference),
            eqTo(validation),
            eqTo(Some("original-location")),
            eqTo(Some("errors-location"))
          )
        ).thenReturn(Future.successful(true))

        service
          .updateFileUploadProcessingDetails(
            monthlyReturn = returnWithUpload,
            reference = uploadReference,
            validation = validation,
            objectStoreFileLocation = Some("original-location"),
            objectStoreFileErrorsLocation = Some("errors-location")
          )
          .futureValue mustBe true
      }

      "must return false when processing details are unchanged" in {
        val validation       =
          FileUploadValidationResult(rowsValidated, validationErrors, FileUploadValidationStatus.ValidationSuccess)
        val returnWithUpload = monthlyReturn.copy(
          fileUploads = List(
            completedFileUpload().copy(
              status = FileUploadStatus.ValidationSuccess,
              fileUploadDetails = Some(fileUploadDetails.copy(validation = Some(validation)))
            )
          )
        )

        when(
          mockMonthlyReturnRepository.updateFileUploadProcessingDetails(
            eqTo(zReference),
            eqTo(taxYear),
            eqTo(month),
            eqTo(uploadReference),
            eqTo(validation),
            eqTo(None),
            eqTo(None)
          )
        ).thenReturn(Future.successful(false))

        service
          .updateFileUploadProcessingDetails(
            monthlyReturn = returnWithUpload,
            reference = uploadReference,
            validation = validation,
            objectStoreFileLocation = None,
            objectStoreFileErrorsLocation = None
          )
          .futureValue mustBe false

        verify(mockMonthlyReturnRepository).updateFileUploadProcessingDetails(
          eqTo(zReference),
          eqTo(taxYear),
          eqTo(month),
          eqTo(uploadReference),
          eqTo(validation),
          eqTo(None),
          eqTo(None)
        )
      }

      "must return false when the file upload has no details" in {
        val validation =
          FileUploadValidationResult(rowsValidated, validationErrors, FileUploadValidationStatus.ValidationSuccess)

        when(
          mockMonthlyReturnRepository.updateFileUploadProcessingDetails(
            eqTo(zReference),
            eqTo(taxYear),
            eqTo(month),
            eqTo(uploadReference),
            eqTo(validation),
            eqTo(Some("original-location")),
            eqTo(None)
          )
        ).thenReturn(Future.successful(false))

        service
          .updateFileUploadProcessingDetails(
            monthlyReturn = monthlyReturn.copy(fileUploads = List(createdFileUpload())),
            reference = uploadReference,
            validation = validation,
            objectStoreFileLocation = Some("original-location"),
            objectStoreFileErrorsLocation = None
          )
          .futureValue mustBe false

        verify(mockMonthlyReturnRepository).updateFileUploadProcessingDetails(
          eqTo(zReference),
          eqTo(taxYear),
          eqTo(month),
          eqTo(uploadReference),
          eqTo(validation),
          eqTo(Some("original-location")),
          eqTo(None)
        )
      }
    }

    "markUpscanExpired" - {

      "must mark an UpscanSuccess file upload as UpscanExpired and persist the MonthlyReturn" in {
        val returnWithUpload = monthlyReturn.copy(fileUploads = List(completedFileUpload()))
        when(
          mockMonthlyReturnRepository
            .markUpscanExpired(eqTo(zReference), eqTo(taxYear), eqTo(month), eqTo(uploadReference))
        )
          .thenReturn(Future.successful(true))

        service
          .markUpscanExpired(
            monthlyReturn = returnWithUpload,
            reference = uploadReference
          )
          .futureValue mustBe true
      }

      "must return false when the file upload is already UpscanExpired" in {
        val returnWithUpload = monthlyReturn.copy(
          fileUploads = List(completedFileUpload().copy(status = FileUploadStatus.UpscanExpired))
        )

        when(
          mockMonthlyReturnRepository
            .markUpscanExpired(eqTo(zReference), eqTo(taxYear), eqTo(month), eqTo(uploadReference))
        )
          .thenReturn(Future.successful(false))

        service
          .markUpscanExpired(
            monthlyReturn = returnWithUpload,
            reference = uploadReference
          )
          .futureValue mustBe false

        verify(mockMonthlyReturnRepository)
          .markUpscanExpired(eqTo(zReference), eqTo(taxYear), eqTo(month), eqTo(uploadReference))
      }

      "must return false when the file upload is not UpscanSuccess" in {
        val returnWithUpload = monthlyReturn.copy(fileUploads = List(createdFileUpload()))

        when(
          mockMonthlyReturnRepository
            .markUpscanExpired(eqTo(zReference), eqTo(taxYear), eqTo(month), eqTo(uploadReference))
        )
          .thenReturn(Future.successful(false))

        service
          .markUpscanExpired(
            monthlyReturn = returnWithUpload,
            reference = uploadReference
          )
          .futureValue mustBe false

        verify(mockMonthlyReturnRepository)
          .markUpscanExpired(eqTo(zReference), eqTo(taxYear), eqTo(month), eqTo(uploadReference))
      }

      "must return false when the file upload reference does not exist" in {
        when(
          mockMonthlyReturnRepository
            .markUpscanExpired(eqTo(zReference), eqTo(taxYear), eqTo(month), eqTo(uploadReference))
        )
          .thenReturn(Future.successful(false))

        service
          .markUpscanExpired(
            monthlyReturn = monthlyReturn,
            reference = uploadReference
          )
          .futureValue mustBe false

        verify(mockMonthlyReturnRepository)
          .markUpscanExpired(eqTo(zReference), eqTo(taxYear), eqTo(month), eqTo(uploadReference))
      }

      "must fail when the repository mark expired fails" in {
        val exception = new RuntimeException(testMongoDownMessage)

        when(
          mockMonthlyReturnRepository
            .markUpscanExpired(eqTo(zReference), eqTo(taxYear), eqTo(month), eqTo(uploadReference))
        )
          .thenReturn(Future.failed(exception))

        service
          .markUpscanExpired(
            monthlyReturn = monthlyReturn,
            reference = uploadReference
          )
          .failed
          .futureValue mustBe exception
      }
    }
  }

  private def buildService(): MonthlyReturnService =
    new MonthlyReturnService(
      monthlyReturnRepository = mockMonthlyReturnRepository,
      returnsSubmissionConnector = mockReturnsSubmissionConnector
    )

  private def createdFileUpload(): FileUpload =
    FileUpload(
      reference = uploadReference,
      status = FileUploadStatus.Created,
      createdOn = testCreatedOn
    )

  private def completedFileUpload(): FileUpload =
    FileUpload(
      reference = uploadReference,
      status = FileUploadStatus.UpscanSuccess,
      createdOn = testCreatedOn,
      fileUploadDetails = Some(fileUploadDetails)
    )

  private val fileUploadDetails = FileUploadDetails(
    fileName = testFileName,
    fileMimeType = testFileMimeType,
    uploadTimestamp = testCreatedOn,
    checksum = testChecksum,
    size = testFileSize,
    upscanDownloadUrl = testDownloadUrl
  )
}
