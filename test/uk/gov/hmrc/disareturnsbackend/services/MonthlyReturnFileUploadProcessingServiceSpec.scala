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
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq as eqTo
import org.mockito.Mockito.{never, verify, when}
import play.api.libs.Files.TemporaryFileCreator
import uk.gov.hmrc.disareturnsbackend.connectors.{ObjectStoreConnector, UpscanConnector}
import uk.gov.hmrc.disareturnsbackend.models.*
import uk.gov.hmrc.disareturnsbackend.repositories.MonthlyReturnRepository
import uk.gov.hmrc.disareturnsbackend.validators.fileupload.*
import uk.gov.hmrc.disareturnsbackend.validators.fileupload.monthly.{MonthlyReturnFileUploadValidationContext, MonthlyReturnFileValidatorSelector}

import java.nio.file.Path
import scala.concurrent.{Future, Promise}

class MonthlyReturnFileUploadProcessingServiceSpec extends SpecBase {

  private val validationSuccess = FileUploadValidationResult(1, 0, FileUploadValidationStatus.ValidationSuccess)
  private val validationFailed  = FileUploadValidationResult(1, 2, FileUploadValidationStatus.ValidationFailed)
  private val invalidFile       = FileUploadValidationResult(0, 0, FileUploadValidationStatus.InvalidFile)

  "MonthlyReturnFileUploadProcessingService" - {

    "must return FileUploadNotFound when the upload is missing" in {
      val fixture = new Fixture(baseMonthlyReturn = monthlyReturn(fileUploads = Nil))

      fixture.service
        .process(fixture.monthlyReturn, testUploadReference)
        .futureValue mustBe FileUploadProcessingResult.FileUploadNotFound
    }

    "must return FileUploadNotReady for a non-UpscanSuccess upload" in {
      val fixture = new Fixture(baseMonthlyReturn =
        monthlyReturn(fileUploads = List(fileUpload(status = FileUploadStatus.Created)))
      )

      fixture.service
        .process(fixture.monthlyReturn, testUploadReference)
        .futureValue mustBe FileUploadProcessingResult.FileUploadNotReady
    }

    "must return FileUploadNotReady when upload details are missing" in {
      val fixture = new Fixture(baseMonthlyReturn = monthlyReturn(fileUploads = List(fileUpload(details = None))))

      fixture.service
        .process(fixture.monthlyReturn, testUploadReference)
        .futureValue mustBe FileUploadProcessingResult.FileUploadNotReady
    }

    "must process a valid file with no errors file" in {
      val fixture =
        new Fixture(validationOutcome = FileUploadValidatorResult(validationSuccess, errorFileWritten = false))

      fixture.service.process(fixture.monthlyReturn, testUploadReference).futureValue mustBe
        FileUploadProcessingResult.Processed(validationSuccess, Some("original-location"), None)

      verify(fixture.upscanConnector).downloadFile(eqTo(testDownloadUrl))(any, any, any)
      verify(fixture.objectStoreConnector).putFile(eqTo(testUploadReference), any[Path], eqTo(testFileMimeType))(any)
      verify(fixture.objectStoreConnector, never())
        .putFile(eqTo(s"$testUploadReference-errors"), any[Path], any[String])(any)
      verify(fixture.repository).updateFileUploadProcessingDetails(
        eqTo(testZReference),
        eqTo(yearOnlyTestTaxYear),
        eqTo(testMonth),
        eqTo(testUploadReference),
        eqTo(validationSuccess),
        eqTo(Some("original-location")),
        eqTo(None)
      )
      verify(fixture.monthlyReturnAuditService).audit(
        eqTo(fixture.monthlyReturn),
        eqTo(testUploadReference),
        eqTo(fileUploadDetails),
        any[FileUploadValidatorResult]
      )
    }

    "must fail without validation, object-store upload, or repository update when the download stream fails" in {
      val fixture = new Fixture(downloadSource = Source.failed(new RuntimeException("download failed")))

      fixture.service.process(fixture.monthlyReturn, testUploadReference).failed.futureValue.getMessage must include(
        "download failed"
      )

      verify(fixture.selector, never()).select(any[String])
      verify(fixture.validator, never()).validate(any[Path], any[Path], any[MonthlyReturnFileUploadValidationContext])
      verify(fixture.objectStoreConnector, never()).putFile(any[String], any[Path], any[String])(any)
      verify(fixture.repository, never())
        .updateFileUploadProcessingDetails(any[String], any[String], any[Int], any[String], any, any, any)
    }

    "must process a validation failure and persist the errors location" in {
      val fixture =
        new Fixture(validationOutcome = FileUploadValidatorResult(validationFailed, errorFileWritten = true))

      fixture.service.process(fixture.monthlyReturn, testUploadReference).futureValue mustBe
        FileUploadProcessingResult.Processed(validationFailed, Some("original-location"), Some("errors-location"))

      verify(fixture.objectStoreConnector).putFile(eqTo(s"$testUploadReference-errors"), any[Path], any[String])(any)
      verify(fixture.repository).updateFileUploadProcessingDetails(
        eqTo(testZReference),
        eqTo(yearOnlyTestTaxYear),
        eqTo(testMonth),
        eqTo(testUploadReference),
        eqTo(validationFailed),
        eqTo(Some("original-location")),
        eqTo(Some("errors-location"))
      )
    }

    "must persist inline validation errors from the validator result" in {
      val validationWithInlineErrors = validationFailed.copy(
        inlineErrors = List(
          FileUploadValidationError(
            rowNumber = 1,
            errorCodes = List(accountNumberRequiredErrorCode, invalidNationalInsuranceErrorCode)
          )
        )
      )
      val fixture                    =
        new Fixture(validationOutcome = FileUploadValidatorResult(validationWithInlineErrors, errorFileWritten = true))

      fixture.service.process(fixture.monthlyReturn, testUploadReference).futureValue mustBe
        FileUploadProcessingResult.Processed(
          validationWithInlineErrors,
          Some("original-location"),
          Some("errors-location")
        )

      verify(fixture.repository).updateFileUploadProcessingDetails(
        eqTo(testZReference),
        eqTo(yearOnlyTestTaxYear),
        eqTo(testMonth),
        eqTo(testUploadReference),
        eqTo(validationWithInlineErrors),
        eqTo(Some("original-location")),
        eqTo(Some("errors-location"))
      )
    }

    "must treat unsupported MIME types as InvalidFile without an errors file" in {
      val fixture = new Fixture(
        selectValidator = Left("application/pdf"),
        details = fileUploadDetails.copy(fileMimeType = "application/pdf")
      )

      fixture.service.process(fixture.monthlyReturn, testUploadReference).futureValue mustBe
        FileUploadProcessingResult.Processed(invalidFile, Some("original-location"), None)

      verify(fixture.repository).updateFileUploadProcessingDetails(
        eqTo(testZReference),
        eqTo(yearOnlyTestTaxYear),
        eqTo(testMonth),
        eqTo(testUploadReference),
        eqTo(invalidFile),
        eqTo(Some("original-location")),
        eqTo(None)
      )
    }

    "must fail when the original object-store upload fails" in {
      val fixture = new Fixture(originalUpload = Future.failed(new RuntimeException("object-store down")))

      fixture.service
        .process(fixture.monthlyReturn, testUploadReference)
        .failed
        .futureValue
        .getMessage mustBe "object-store down"
    }

    "must fail when the errors object-store upload fails" in {
      val fixture = new Fixture(
        validationOutcome = FileUploadValidatorResult(validationFailed, errorFileWritten = true),
        errorsUpload = Future.failed(new RuntimeException("errors upload down"))
      )

      fixture.service
        .process(fixture.monthlyReturn, testUploadReference)
        .failed
        .futureValue
        .getMessage mustBe "errors upload down"
    }

    "must return MonthlyReturnUpdateFailed when repository update returns false" in {
      val fixture = new Fixture(repositoryUpdate = false)

      fixture.service
        .process(fixture.monthlyReturn, testUploadReference)
        .futureValue mustBe FileUploadProcessingResult.MonthlyReturnUpdateFailed

      verify(fixture.monthlyReturnAuditService, never()).audit(any, any, any, any)
    }

    "must not wait for audit delivery before completing processing" in {
      val auditCompletion = Promise[Unit]()
      val fixture         = new Fixture(auditResult = auditCompletion.future)

      fixture.service.process(fixture.monthlyReturn, testUploadReference).futureValue mustBe
        FileUploadProcessingResult.Processed(validationSuccess, Some("original-location"), None)

      verify(fixture.monthlyReturnAuditService).audit(
        eqTo(fixture.monthlyReturn),
        eqTo(testUploadReference),
        eqTo(fileUploadDetails),
        any[FileUploadValidatorResult]
      )
    }
  }

  private class Fixture(
    details: FileUploadDetails = fileUploadDetails,
    baseMonthlyReturn: MonthlyReturn = monthlyReturn(),
    selectValidator: Either[String, FileUploadValidator[MonthlyReturnFileUploadValidationContext]] = Right(
      mock[FileUploadValidator[MonthlyReturnFileUploadValidationContext]]
    ),
    validationOutcome: FileUploadValidatorResult =
      FileUploadValidatorResult(validationSuccess, errorFileWritten = false),
    originalUpload: Future[String] = Future.successful("original-location"),
    errorsUpload: Future[String] = Future.successful("errors-location"),
    repositoryUpdate: Boolean = true,
    downloadSource: Source[ByteString, ?] = Source.single(ByteString("file-content")),
    auditResult: Future[Unit] = Future.successful(())
  ) {
    val validator: FileUploadValidator[MonthlyReturnFileUploadValidationContext] =
      selectValidator.getOrElse(mock[FileUploadValidator[MonthlyReturnFileUploadValidationContext]])
    val monthlyReturn: MonthlyReturn                                             = baseMonthlyReturn.copy(
      fileUploads = baseMonthlyReturn.fileUploads.map(upload =>
        upload.copy(fileUploadDetails = upload.fileUploadDetails.map(_ => details))
      )
    )
    val upscanConnector: UpscanConnector                                         = mock[UpscanConnector]
    val selector: MonthlyReturnFileValidatorSelector                             = mock[MonthlyReturnFileValidatorSelector]
    val objectStoreConnector: ObjectStoreConnector                               = mock[ObjectStoreConnector]
    val repository: MonthlyReturnRepository                                      = mock[MonthlyReturnRepository]
    val monthlyReturnAuditService: MonthlyReturnAuditService                     = mock[MonthlyReturnAuditService]

    when(upscanConnector.downloadFile(any[String])(any, any, any))
      .thenReturn(Future.successful(downloadSource))
    when(selector.select(any[String])).thenReturn(selectValidator)
    when(validator.validate(any[Path], any[Path], any[MonthlyReturnFileUploadValidationContext]))
      .thenReturn(Future.successful(validationOutcome))
    when(objectStoreConnector.putFile(eqTo(testUploadReference), any[Path], any[String])(any))
      .thenReturn(originalUpload)
    when(objectStoreConnector.putFile(eqTo(s"$testUploadReference-errors"), any[Path], any[String])(any))
      .thenReturn(errorsUpload)
    when(repository.updateFileUploadProcessingDetails(any[String], any[String], any[Int], any[String], any, any, any))
      .thenReturn(Future.successful(repositoryUpdate))
    when(
      monthlyReturnAuditService
        .audit(any[MonthlyReturn], any[String], any[FileUploadDetails], any[FileUploadValidatorResult])
    )
      .thenReturn(auditResult)

    val service = new MonthlyReturnFileUploadProcessingServiceImpl(
      temporaryFileCreator = inject[TemporaryFileCreator],
      upscanConnector = upscanConnector,
      monthlyReturnFileValidatorSelector = selector,
      objectStoreConnector = objectStoreConnector,
      monthlyReturnRepository = repository,
      monthlyReturnAuditService = monthlyReturnAuditService
    )(ec, inject[Materializer])
  }

  private def monthlyReturn(fileUploads: List[FileUpload] = List(fileUpload())): MonthlyReturn =
    MonthlyReturn(
      zReference = testZReference,
      submissionId = testSubmissionId,
      taxYear = yearOnlyTestTaxYear,
      month = testMonth,
      createdOn = testCreatedOn,
      fileUploads = fileUploads,
      lastUpdated = testCreatedOn
    )

  private def fileUpload(
    status: FileUploadStatus = FileUploadStatus.UpscanSuccess,
    details: Option[FileUploadDetails] = Some(fileUploadDetails)
  ): FileUpload =
    FileUpload(testUploadReference, status, testCreatedOn, details)

  private def fileUploadDetails: FileUploadDetails =
    FileUploadDetails(
      fileName = testFileName,
      fileMimeType = testFileMimeType,
      uploadTimestamp = testCreatedOn,
      checksum = testChecksum,
      size = testFileSize,
      upscanDownloadUrl = testDownloadUrl
    )
}
