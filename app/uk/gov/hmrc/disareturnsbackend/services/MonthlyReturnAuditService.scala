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

import play.api.Logging
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.disareturnsbackend.config.AppConfig
import uk.gov.hmrc.disareturnsbackend.models.*
import uk.gov.hmrc.disareturnsbackend.validators.fileupload.FileUploadValidatorResult
import uk.gov.hmrc.play.audit.http.connector.AuditResult.{Disabled, Failure, Success}
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent

import java.time.{Clock, Duration, Instant, YearMonth}
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MonthlyReturnAuditService @Inject() (
  auditConnector: AuditConnector,
  appConfig: AppConfig,
  clock: Clock
)(implicit ec: ExecutionContext)
    extends Logging {

  import MonthlyReturnAuditService.*

  def audit(
    monthlyReturn: MonthlyReturn,
    fileUploadReference: String,
    fileUploadDetails: FileUploadDetails,
    validationOutcome: FileUploadValidatorResult
  ): Future[Unit] = {
    val validation = validationOutcome.validation
    val detail     = baseDetail(monthlyReturn, fileUploadReference, fileUploadDetails, validationOutcome) ++
      failureDetail(validation, validationOutcome.errorVolumes)
    val event      = ExtendedDataEvent(
      auditSource = appConfig.appName,
      auditType = fileUploadValidation,
      detail = detail
    )

    auditConnector
      .sendExtendedEvent(event)
      .map(logResult(fileUploadValidation))
  }

  def auditUpscanValidationSuccess(
    monthlyReturn: MonthlyReturn,
    fileUpload: FileUpload,
    fileUploadDetails: FileUploadDetails
  ): Future[Unit] = {
    val event = ExtendedDataEvent(
      auditSource = appConfig.appName,
      auditType = upscanValidation,
      detail = baseUpscanValidationDetail(
        monthlyReturn = monthlyReturn,
        fileUpload = fileUpload,
        upscanStatus = successStatusValue,
        fileName = fileUploadDetails.fileName
      )
    )

    auditConnector
      .sendExtendedEvent(event)
      .map(logResult(upscanValidation))
  }

  def auditUpscanValidationFailure(
    monthlyReturn: MonthlyReturn,
    fileUpload: FileUpload,
    failureReason: FileUploadFailureReason,
    failureMessage: String
  ): Future[Unit] = {
    val event = ExtendedDataEvent(
      auditSource = appConfig.appName,
      auditType = upscanValidation,
      detail = baseUpscanValidationDetail(
        monthlyReturn = monthlyReturn,
        fileUpload = fileUpload,
        upscanStatus = failureStatusValue,
        fileName = absentDueToFailureFileName
      ) ++ Json.obj(
        failureReasonField  -> failureReason.value,
        failureMessageField -> failureMessage
      )
    )

    auditConnector
      .sendExtendedEvent(event)
      .map(logResult(upscanValidation))
  }

  def auditDuplicateFileUploadValidation(
    monthlyReturn: MonthlyReturn,
    fileUploadReference: String,
    fileUploadDetails: FileUploadDetails
  ): Future[Unit] =
    audit(
      monthlyReturn = monthlyReturn,
      fileUploadReference = fileUploadReference,
      fileUploadDetails = fileUploadDetails,
      validationOutcome = FileUploadValidatorResult(
        validation = FileUploadValidationResult(
          rowsValidated = 0,
          validationErrors = 1,
          status = FileUploadValidationStatus.ValidationFailed
        ),
        errorFileWritten = false,
        errorVolumes = Map(duplicateFileErrorType -> 1L),
        validationTimeMillis = 0
      )
    )

  private def baseDetail(
    monthlyReturn: MonthlyReturn,
    fileUploadReference: String,
    fileUploadDetails: FileUploadDetails,
    validationOutcome: FileUploadValidatorResult
  ): JsObject =
    Json.obj(
      internalReturnIdField   -> monthlyReturn.submissionId.toString,
      fileUploadStatusField   -> fileUploadStatus(validationOutcome.validation.status),
      fileSizeField           -> fileUploadDetails.size.toString,
      fileValidationTimeField -> validationOutcome.validationTimeMillis.toString,
      numberOfEntriesField    -> validationOutcome.validation.rowsValidated.toString,
      fileReferenceField      -> fileUploadReference,
      fileNameField           -> fileUploadDetails.fileName,
      periodField             -> reportingPeriod(monthlyReturn)
    )

  private def failureDetail(validation: FileUploadValidationResult, errorVolumes: Map[String, Long]): JsObject =
    if (validation.status == FileUploadValidationStatus.ValidationSuccess) {
      Json.obj()
    } else {
      val auditErrorVolumes =
        if (validation.status == FileUploadValidationStatus.InvalidFile) {
          Map(invalidFileErrorType -> 1L)
        } else {
          errorVolumes
        }

      Json.obj(
        errorDetailsField -> auditErrorVolumes.toSeq.sortBy(_._1).map { case (errorType, volume) =>
          Json.obj(errorTypeField -> errorType, volumeField -> volume)
        }
      )
    }

  private def baseUpscanValidationDetail(
    monthlyReturn: MonthlyReturn,
    fileUpload: FileUpload,
    upscanStatus: String,
    fileName: String
  ): JsObject =
    Json.obj(
      internalReturnIdField     -> monthlyReturn.submissionId.toString,
      upscanStatusField         -> upscanStatus,
      upscanValidationTimeField -> upscanValidationTime(fileUpload.createdOn),
      fileReferenceField        -> fileUpload.reference,
      fileNameField             -> fileName,
      periodField               -> reportingPeriod(monthlyReturn)
    )

  private def fileUploadStatus(validationStatus: FileUploadValidationStatus): String =
    validationStatus match {
      case FileUploadValidationStatus.ValidationSuccess => successStatusValue
      case _                                            => failureStatusValue
    }

  private def upscanValidationTime(startedAt: Instant): String =
    Duration.between(startedAt, Instant.now(clock)).toMillis.toString

  private def reportingPeriod(monthlyReturn: MonthlyReturn): String = {
    val startYear           = monthlyReturn.taxYear.take(4).toInt
    val reportingWindowYear = if (monthlyReturn.month >= 4) startYear else startYear + 1

    YearMonth
      .of(reportingWindowYear, monthlyReturn.month)
      .minusMonths(1)
      .format(reportingPeriodFormatter)
  }

  private def logResult(auditType: String)(result: AuditResult): Unit =
    result match {
      case Success         => logger.info(s"$auditType audit successful")
      case Failure(err, _) => logger.warn(s"$auditType audit failed: $err")
      case Disabled        => logger.warn(s"$auditType audit skipped because auditing is disabled")
    }
}

object MonthlyReturnAuditService {
  val fileUploadValidation = "FileUploadValidation"
  val upscanValidation     = "UpscanValidation"

  val internalReturnIdField     = "internalReturnId"
  val fileUploadStatusField     = "fileUploadStatus"
  val fileSizeField             = "fileSize"
  val fileValidationTimeField   = "fileValidationTime"
  val numberOfEntriesField      = "numberOfEntries"
  val fileReferenceField        = "fileReference"
  val fileNameField             = "fileName"
  val periodField               = "period"
  val errorDetailsField         = "errorDetails"
  val errorTypeField            = "errorType"
  val volumeField               = "volume"
  val upscanStatusField         = "upscanStatus"
  val upscanValidationTimeField = "upscanValidationTime"
  val failureReasonField        = "failureReason"
  val failureMessageField       = "failureMessage"
  val invalidFileErrorType      = "InvalidFile"
  val duplicateFileErrorType    = "DuplicateFile"

  val successStatusValue = "Success"
  val failureStatusValue = "Failure"

  val absentDueToFailureFileName = "absent due to failure"

  val reportingPeriodFormatter = DateTimeFormatter.ofPattern("MMMM uuuu", Locale.UK)
}
