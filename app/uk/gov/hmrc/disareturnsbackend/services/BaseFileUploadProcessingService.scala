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

import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{FileIO, Source}
import org.apache.pekko.util.ByteString
import play.api.Logging
import play.api.libs.Files.TemporaryFileCreator
import uk.gov.hmrc.disareturnsbackend.connectors.*
import uk.gov.hmrc.disareturnsbackend.models.*
import uk.gov.hmrc.disareturnsbackend.utils.Constants.XLSX_MIME_TYPE
import uk.gov.hmrc.disareturnsbackend.utils.TempFileSupport
import uk.gov.hmrc.disareturnsbackend.validators.fileupload.*
import uk.gov.hmrc.http.HeaderCarrier

import java.nio.file.Path
import scala.concurrent.duration.NANOSECONDS
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Failure
import scala.util.control.NonFatal

abstract class BaseFileUploadProcessingService[R, C <: FileUploadValidationContext](
  override protected val temporaryFileCreator: TemporaryFileCreator,
  upscanConnector: UpscanConnector,
  fileValidatorSelector: FileUploadValidatorSelector[C],
  objectStoreConnector: ObjectStoreConnector
)(implicit ec: ExecutionContext, materializer: Materializer)
    extends Logging
    with TempFileSupport {

  protected def serviceName: String

  protected def getFileUpload(returnRecord: R, fileUploadReference: String): Option[FileUpload]

  protected def validationContext(returnRecord: R): C

  protected def updateFileUploadProcessingDetails(
    returnRecord: R,
    fileUploadReference: String,
    validation: FileUploadValidationResult,
    objectStoreFileLocation: Option[String],
    objectStoreFileErrorsLocation: Option[String]
  ): Future[Boolean]

  protected def auditFileUploadValidation(
    returnRecord: R,
    fileUploadReference: String,
    fileUploadDetails: FileUploadDetails,
    validationOutcome: FileUploadValidatorResult
  ): Future[Unit]

  protected def describeReturn(returnRecord: R): String

  final def process(returnRecord: R, fileUploadReference: String): Future[FileUploadProcessingResult] = {
    val context = logContext(returnRecord, fileUploadReference)
    logger.info(s"${logPrefix(fileUploadReference)} Processing started for $context")

    getFileUpload(returnRecord, fileUploadReference) match {
      case None =>
        logger.warn(s"${logPrefix(fileUploadReference)} File upload reference not found for $context")
        Future.successful(FileUploadProcessingResult.FileUploadNotFound)

      case Some(fileUpload) if fileUpload.status != FileUploadStatus.UpscanSuccess =>
        logger.warn(
          s"${logPrefix(fileUploadReference)} File upload for $context is not ready: status [${fileUpload.status}]"
        )
        Future.successful(FileUploadProcessingResult.FileUploadNotReady)

      case Some(fileUpload) =>
        fileUpload.fileUploadDetails match {
          case None          =>
            logger.warn(s"${logPrefix(fileUploadReference)} File upload details missing for $context")
            Future.successful(FileUploadProcessingResult.FileUploadNotReady)
          case Some(details) =>
            processReadyFile(returnRecord, fileUploadReference, details).andThen { case Failure(exception) =>
              logger.error(s"${logPrefix(fileUploadReference)} Processing failed unexpectedly for $context", exception)
            }
        }
    }
  }

  private def processReadyFile(
    returnRecord: R,
    uploadReference: String,
    details: FileUploadDetails
  ): Future[FileUploadProcessingResult] = {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    val context                    = logContext(returnRecord, uploadReference)

    logger.info(
      s"${logPrefix(uploadReference)} Ready file processing started for $context, file name [${details.fileName}], MIME type [${details.fileMimeType}], declared file size [${details.size}]"
    )

    withTempFile(uploadReference) { filePath =>
      withTempFile(uploadReference + "-errors") { fileUploadErrors =>
        for {
          downloadStream                <- downloadFromUpscan(details, uploadReference, context)
          _                             <- writeDownloadStream(downloadStream, filePath, uploadReference, context)
          validationOutcome             <-
            validateFileUpload(returnRecord, details, filePath, fileUploadErrors, uploadReference, context)
          objectStoreFileLocation       <- uploadFileToObjectStore(uploadReference, filePath, details, context)
          objectStoreFileErrorsLocation <-
            maybeUploadErrorsToObjectStore(uploadReference, fileUploadErrors, validationOutcome, context)
          fileUploadUpdated             <- updateFileUploadInRepository(
                                             returnRecord,
                                             uploadReference,
                                             validationOutcome,
                                             objectStoreFileLocation,
                                             objectStoreFileErrorsLocation,
                                             context
                                           )
        } yield (
          fileUploadUpdated,
          validationOutcome,
          objectStoreFileLocation,
          objectStoreFileErrorsLocation
        )
      }.flatMap { case (fileUploadUpdated, validationOutcome, objectStoreFileLocation, objectStoreFileErrorsLocation) =>
        if (fileUploadUpdated) {
          logger.info(s"${logPrefix(uploadReference)} Processing completed successfully for $context")
          submitFileUploadValidationAudit(returnRecord, uploadReference, details, validationOutcome, context)
          Future.successful(
            FileUploadProcessingResult.Processed(
              validation = validationOutcome.validation,
              objectStoreFileLocation = objectStoreFileLocation,
              objectStoreFileErrorsLocation = objectStoreFileErrorsLocation
            )
          )
        } else {
          logger.warn(s"${logPrefix(uploadReference)} Repository update returned false for $context")
          Future.successful(FileUploadProcessingResult.MonthlyReturnUpdateFailed)
        }
      }
    }
  }

  private def downloadFromUpscan(
    details: FileUploadDetails,
    uploadReference: String,
    context: String
  )(implicit hc: HeaderCarrier): Future[Source[ByteString, ?]] = {
    logger.info(s"${logPrefix(uploadReference)} Upscan download started for $context")
    upscanConnector.downloadFile(details.upscanDownloadUrl)
  }

  private def writeDownloadStream(
    downloadStream: Source[ByteString, ?],
    filePath: Path,
    uploadReference: String,
    context: String
  ): Future[Unit] =
    writeDownloadStreamToFile(downloadStream, filePath).map { downloadedBytes =>
      logger.info(
        s"${logPrefix(uploadReference)} Upscan download written to temp file for $context, downloaded byte count [$downloadedBytes]"
      )
    }

  private def validateFileUpload(
    returnRecord: R,
    details: FileUploadDetails,
    filePath: Path,
    fileUploadErrors: Path,
    uploadReference: String,
    context: String
  ): Future[FileUploadValidatorResult] =
    fileValidatorSelector.select(details.fileMimeType) match {
      case Right(validator) =>
        val validationStartedAt = System.nanoTime()
        logger.info(
          s"${logPrefix(uploadReference)} Validator selected for $context, MIME type [${details.fileMimeType}], validator [${validator.getClass.getSimpleName}]"
        )
        validator
          .validate(
            file = filePath,
            errorsFile = fileUploadErrors,
            context = validationContext(returnRecord)
          )
          .map { validationOutcome =>
            val timedValidationOutcome = validationOutcome.copy(
              validationTimeMillis = elapsedMillis(validationStartedAt)
            )
            logger.info(
              s"${logPrefix(uploadReference)} Validation completed for $context, status [${timedValidationOutcome.validation.status}], rows validated [${timedValidationOutcome.validation.rowsValidated}], validation errors [${timedValidationOutcome.validation.validationErrors}], errors file written [${timedValidationOutcome.errorFileWritten}], validation time milliseconds [${timedValidationOutcome.validationTimeMillis}]"
            )
            timedValidationOutcome
          }

      case Left(unsupportedMimeType) =>
        logger.warn(
          s"${logPrefix(uploadReference)} Unsupported MIME type [$unsupportedMimeType] treated as InvalidFile for $context"
        )
        val validationOutcome = invalidFileValidationResult
        logger.info(
          s"${logPrefix(uploadReference)} Validation completed for $context, status [${validationOutcome.validation.status}], rows validated [${validationOutcome.validation.rowsValidated}], validation errors [${validationOutcome.validation.validationErrors}], errors file written [${validationOutcome.errorFileWritten}]"
        )
        Future.successful(validationOutcome)
    }

  private def uploadFileToObjectStore(
    uploadReference: String,
    filePath: Path,
    details: FileUploadDetails,
    context: String
  )(implicit hc: HeaderCarrier): Future[Option[String]] = {
    logger.info(s"${logPrefix(uploadReference)} Original file object-store upload started for $context")
    objectStoreConnector
      .putFile(
        objectName = uploadReference,
        file = filePath,
        contentType = details.fileMimeType
      )
      .map { location =>
        logger.info(
          s"${logPrefix(uploadReference)} Original file object-store upload completed for $context, object-store location [$location]"
        )
        Some(location)
      }
  }

  private def maybeUploadErrorsToObjectStore(
    uploadReference: String,
    errorsFilePath: Path,
    validationOutcome: FileUploadValidatorResult,
    context: String
  )(implicit hc: HeaderCarrier): Future[Option[String]] =
    if (validationOutcome.errorFileWritten) {
      logger.info(s"${logPrefix(uploadReference)} Error file object-store upload started for $context")
      objectStoreConnector
        .putFile(
          objectName = s"$uploadReference-errors",
          file = errorsFilePath,
          contentType = XLSX_MIME_TYPE
        )
        .map { location =>
          logger.info(
            s"${logPrefix(uploadReference)} Error file object-store upload completed for $context, object-store location [$location]"
          )
          Some(location)
        }
    } else {
      logger.info(
        s"${logPrefix(uploadReference)} Error file object-store upload skipped for $context because no errors file exists"
      )
      Future.successful(None)
    }

  private def updateFileUploadInRepository(
    returnRecord: R,
    uploadReference: String,
    validationOutcome: FileUploadValidatorResult,
    objectStoreFileLocation: Option[String],
    objectStoreFileErrorsLocation: Option[String],
    context: String
  ): Future[Boolean] = {
    logger.info(s"${logPrefix(uploadReference)} Repository update started for $context")
    updateFileUploadProcessingDetails(
      returnRecord = returnRecord,
      fileUploadReference = uploadReference,
      validation = validationOutcome.validation,
      objectStoreFileLocation = objectStoreFileLocation,
      objectStoreFileErrorsLocation = objectStoreFileErrorsLocation
    )
  }

  private def invalidFileValidationResult: FileUploadValidatorResult =
    FileUploadValidatorResult(
      validation = FileUploadValidationResult(
        rowsValidated = 0,
        validationErrors = 0,
        status = FileUploadValidationStatus.InvalidFile
      ),
      errorFileWritten = false
    )

  private def elapsedMillis(startedAtNanos: Long): Long =
    NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos)

  private def submitFileUploadValidationAudit(
    returnRecord: R,
    uploadReference: String,
    details: FileUploadDetails,
    validationOutcome: FileUploadValidatorResult,
    context: String
  ): Unit =
    try {
      auditFileUploadValidation(returnRecord, uploadReference, details, validationOutcome)
        .recover { case NonFatal(exception) =>
          logger.error(s"${logPrefix(uploadReference)} FileUploadValidation audit failed for $context", exception)
        }
      ()
    } catch {
      case NonFatal(exception) =>
        logger.error(
          s"${logPrefix(uploadReference)} FileUploadValidation audit could not be submitted for $context",
          exception
        )
    }

  private def logPrefix(fileUploadReference: String): String =
    s"[$serviceName][process][fileUploadReference=$fileUploadReference]"

  private def logContext(returnRecord: R, fileUploadReference: String): String =
    s"${describeReturn(returnRecord)}, upload reference [$fileUploadReference]"

  private def writeDownloadStreamToFile(source: Source[ByteString, ?], target: Path): Future[Long] =
    source.runWith(FileIO.toPath(target)).map(_.count)
}
