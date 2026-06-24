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

import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AuditService @Inject() (
  auditConnector: AuditConnector,
  appConfig: AppConfig
)(implicit ec: ExecutionContext)
    extends Logging {

  import AuditService.*

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
      auditType = FileUploadValidation,
      detail = detail
    )

    auditConnector
      .sendExtendedEvent(event)
      .map(logResult)
  }

  private def baseDetail(
    monthlyReturn: MonthlyReturn,
    fileUploadReference: String,
    fileUploadDetails: FileUploadDetails,
    validationOutcome: FileUploadValidatorResult
  ): JsObject =
    Json.obj(
      InternalReturnIdField   -> monthlyReturn.submissionId.toString,
      FileUploadStatusField   -> fileUploadStatus(validationOutcome.validation.status),
      DownloadTimeField       -> fileUploadDetails.upscanCompletedOn
        .getOrElse(fileUploadDetails.uploadTimestamp)
        .toEpochMilli
        .toString,
      FileSizeField           -> fileUploadDetails.size.toString,
      FileValidationTimeField -> validationOutcome.validationTimeMillis.toString,
      NumberOfEntriesField    -> validationOutcome.validation.rowsValidated.toString,
      FileReferenceField      -> fileUploadReference,
      FileNameField           -> fileUploadDetails.fileName,
      PeriodField             -> reportingPeriod(monthlyReturn)
    )

  private def failureDetail(validation: FileUploadValidationResult, errorVolumes: Map[String, Long]): JsObject =
    if (validation.status == FileUploadValidationStatus.ValidationSuccess) {
      Json.obj()
    } else {
      val auditErrorVolumes =
        if (validation.status == FileUploadValidationStatus.InvalidFile) {
          Map(InvalidFileErrorType -> 1L)
        } else {
          errorVolumes
        }

      Json.obj(
        ErrorDetailsField -> auditErrorVolumes.toSeq.sortBy(_._1).map { case (errorType, volume) =>
          Json.obj(ErrorTypeField -> errorType, VolumeField -> volume)
        }
      )
    }

  private def fileUploadStatus(validationStatus: FileUploadValidationStatus): String =
    validationStatus match {
      case FileUploadValidationStatus.ValidationSuccess => SuccessStatusValue
      case _                                            => FailureStatusValue
    }

  private def reportingPeriod(monthlyReturn: MonthlyReturn): String = {
    val startYear           = monthlyReturn.taxYear.take(4).toInt
    val reportingWindowYear = if (monthlyReturn.month >= 4) startYear else startYear + 1

    YearMonth
      .of(reportingWindowYear, monthlyReturn.month)
      .minusMonths(1)
      .format(ReportingPeriodFormatter)
  }

  private def logResult(result: AuditResult): Unit =
    result match {
      case Success         => logger.info(s"$FileUploadValidation audit successful")
      case Failure(err, _) => logger.warn(s"$FileUploadValidation audit failed: $err")
      case Disabled        => logger.warn(s"$FileUploadValidation audit skipped because auditing is disabled")
    }
}

object AuditService {
  val FileUploadValidation = "FileUploadValidation"

  val InternalReturnIdField   = "internalReturnId"
  val FileUploadStatusField   = "fileUploadStatus"
  val DownloadTimeField       = "downloadTime"
  val FileSizeField           = "fileSize"
  val FileValidationTimeField = "fileValidationTime"
  val NumberOfEntriesField    = "numberOfEntries"
  val FileReferenceField      = "fileReference"
  val FileNameField           = "fileName"
  val PeriodField             = "period"
  val ErrorDetailsField       = "errorDetails"
  val ErrorTypeField          = "errorType"
  val VolumeField             = "volume"
  val InvalidFileErrorType    = "InvalidFile"

  val SuccessStatusValue = "Success"
  val FailureStatusValue = "Failure"

  val ReportingPeriodFormatter = DateTimeFormatter.ofPattern("MMMM uuuu", Locale.UK)
}
