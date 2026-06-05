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
import uk.gov.hmrc.disareturnsbackend.mappers.UpscanCallbackMapper
import uk.gov.hmrc.disareturnsbackend.models.*
import uk.gov.hmrc.disareturnsbackend.repositories.MonthlyReturnRepository

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class UpscanCallbackService @Inject() (
  monthlyReturnRepository: MonthlyReturnRepository,
  upscanCallbackMapper: UpscanCallbackMapper
)(implicit ec: ExecutionContext)
    extends Logging {

  def monthlyReturnUpscanCallback(
    zReference: String,
    taxYear: String,
    month: String,
    upscanResult: UpscanResult
  ): Future[Unit] =
    upscanResult match {
      case success: UpscanSuccess =>
        val status = FileUploadStatus.UpscanSuccess

        monthlyReturnRepository
          .completeFileUpload(
            zReference = zReference,
            taxYear = taxYear,
            month = month,
            reference = success.reference,
            status = status,
            fileUploadDetails = Some(upscanCallbackMapper.toFileUploadDetails(success.uploadDetails)),
            downloadUrl = Some(success.downloadUrl),
            failureReason = None,
            failureMessage = None
          )
          .map(logCallbackResult(zReference, taxYear, month, success.reference, status))
          .recoverWith { case NonFatal(exception) =>
            logCallbackFailure(zReference, taxYear, month, success.reference, status, exception)
            Future.failed(exception)
          }

      case failure: UpscanFailure =>
        val status = upscanCallbackMapper.toFileUploadStatus(failure.failureDetails.failureReason)

        monthlyReturnRepository
          .completeFileUpload(
            zReference = zReference,
            taxYear = taxYear,
            month = month,
            reference = failure.reference,
            status = status,
            fileUploadDetails = None,
            downloadUrl = None,
            failureReason = Some(upscanCallbackMapper.toFileUploadFailureReason(failure.failureDetails.failureReason)),
            failureMessage = Some(failure.failureDetails.message)
          )
          .map(logCallbackResult(zReference, taxYear, month, failure.reference, status))
          .recoverWith { case NonFatal(exception) =>
            logCallbackFailure(zReference, taxYear, month, failure.reference, status, exception)
            Future.failed(exception)
          }
    }

  private def logCallbackResult(
    zReference: String,
    taxYear: String,
    month: String,
    reference: String,
    status: FileUploadStatus
  )(updated: Boolean): Unit =
    if (updated) {
      logger.info(
        s"[UpscanCallbackService][logCallbackResult] Completed upscan callback for zReference [$zReference], taxYear [$taxYear], month [$month], upload reference [$reference], status [${status.value}]"
      )
    } else {
      logger.warn(
        s"[UpscanCallbackService][logCallbackResult] Unable to complete upscan callback for zReference [$zReference], taxYear [$taxYear], month [$month], upload reference [$reference], status [${status.value}]. No matching file upload found"
      )
    }

  private def logCallbackFailure(
    zReference: String,
    taxYear: String,
    month: String,
    reference: String,
    status: FileUploadStatus,
    exception: Throwable
  ): Unit =
    logger.error(
      s"[UpscanCallbackService][logCallbackFailure] Failed to complete upscan callback for zReference [$zReference], taxYear [$taxYear], month [$month], upload reference [$reference], status [${status.value}]",
      exception
    )

}
