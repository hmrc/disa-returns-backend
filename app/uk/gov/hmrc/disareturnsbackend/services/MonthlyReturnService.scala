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
import play.api.libs.json.JsValue
import uk.gov.hmrc.disareturnsbackend.connectors.ReturnsSubmissionConnector
import uk.gov.hmrc.disareturnsbackend.connectors.ReturnsSubmissionConnector.*
import uk.gov.hmrc.disareturnsbackend.config.AppConfig
import uk.gov.hmrc.disareturnsbackend.models.*
import uk.gov.hmrc.disareturnsbackend.repositories.*
import uk.gov.hmrc.disareturnsbackend.services.CreateFileUploadResult.*
import uk.gov.hmrc.disareturnsbackend.services.CreateMonthlyReturnResult.*
import uk.gov.hmrc.http.HeaderCarrier

import java.time.{Clock, Instant, LocalDate}
import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class MonthlyReturnService @Inject() (
  monthlyReturnRepository: MonthlyReturnRepository,
  returnsSubmissionConnector: ReturnsSubmissionConnector,
  appConfig: AppConfig,
  clock: Clock
)(implicit ec: ExecutionContext)
    extends Logging {

  def get(zReference: String, taxYear: String, month: Int): Future[Option[MonthlyReturn]] =
    monthlyReturnRepository
      .get(zReference, taxYear, month)
      .map { maybeMonthlyReturn =>
        maybeMonthlyReturn match {
          case Some(_) =>
            logger.info(
              s"[MonthlyReturnService][get] Found monthly return for zReference [$zReference], taxYear [$taxYear], month [$month]"
            )
          case None    =>
            logger.warn(
              s"[MonthlyReturnService][get] No monthly return found for zReference [$zReference], taxYear [$taxYear], month [$month]"
            )
        }

        maybeMonthlyReturn
      }

  def getWithDeclaration(zReference: String, taxYear: String, month: Int)(implicit
    hc: HeaderCarrier
  ): Future[Option[MonthlyReturnResponse]] =
    get(zReference, taxYear, month)
      .flatMap {
        case Some(monthlyReturn) =>
          returnsSubmissionConnector
            .getMonthlyReturn(zReference, taxYear, month)
            .map(submissionMonthlyReturn =>
              Some(MonthlyReturnResponse(monthlyReturn, submissionMonthlyReturn.flatMap(declaredOn)))
            )

        case None =>
          Future.successful(None)
      }
      .recoverWith { case NonFatal(exception) =>
        logger.error(
          s"[MonthlyReturnService][get] Failed to get monthly return for zReference [$zReference], taxYear [$taxYear], month [$month]",
          exception
        )
        Future.failed(exception)
      }

  def create(
    zReference: String,
    taxYear: String,
    month: Int,
    nilReturn: Boolean
  )(implicit hc: HeaderCarrier): Future[CreateMonthlyReturnResult] =
    monthlyReturnRepository
      .get(zReference, taxYear, month)
      .flatMap {
        case Some(_) =>
          logger.warn(
            s"[MonthlyReturnService][create] Monthly return already exists in backend for zReference [$zReference], taxYear [$taxYear], month [$month], nilReturn [$nilReturn]"
          )
          Future.successful(AlreadyExists)

        case None =>
          logger.info(
            s"[MonthlyReturnService][create] Creating monthly return in submission for zReference [$zReference], taxYear [$taxYear], month [$month], nilReturn [$nilReturn]"
          )

          returnsSubmissionConnector
            .createMonthlyReturn(zReference, taxYear, month, nilReturn)
            .flatMap {
              case CreateMonthlyReturnSubmissionResult.Created(submissionId) =>
                logger.info(
                  s"[MonthlyReturnService][create] Created monthly return in submission for zReference [$zReference], taxYear [$taxYear], month [$month], submissionId [$submissionId], nilReturn [$nilReturn]"
                )
                createBackendMonthlyReturn(zReference, taxYear, month, submissionId, nilReturn)

              case CreateMonthlyReturnSubmissionResult.AlreadyExists(submissionId) =>
                logger.warn(
                  s"[MonthlyReturnService][create] Monthly return already exists in submission but not backend for zReference [$zReference], taxYear [$taxYear], month [$month], submissionId [$submissionId], nilReturn [$nilReturn]"
                )
                createBackendMonthlyReturn(zReference, taxYear, month, submissionId, nilReturn)

              case CreateMonthlyReturnSubmissionResult.OutsideDeclarationPeriod =>
                logger.warn(
                  s"[MonthlyReturnService][create] Submission rejected monthly return outside declaration period for zReference [$zReference], taxYear [$taxYear], month [$month], nilReturn [$nilReturn]"
                )
                Future.successful(OutsideDeclarationPeriod)
            }
      }
      .recoverWith { case NonFatal(exception) =>
        logger.error(
          s"[MonthlyReturnService][create] Failed to create monthly return for zReference [$zReference], taxYear [$taxYear], month [$month], nilReturn [$nilReturn]",
          exception
        )
        Future.failed(exception)
      }

  private def createBackendMonthlyReturn(
    zReference: String,
    taxYear: String,
    month: Int,
    submissionId: UUID,
    nilReturn: Boolean
  ): Future[CreateMonthlyReturnResult] =
    monthlyReturnRepository
      .create(zReference, taxYear, month, submissionId, nilReturn)
      .map {
        case Some(monthlyReturn) =>
          logger.info(
            s"[MonthlyReturnService][createBackendMonthlyReturn] Created backend monthly return for zReference [$zReference], taxYear [$taxYear], month [$month], submissionId [$submissionId], nilReturn [$nilReturn]"
          )
          Created(monthlyReturn.submissionId)

        case None =>
          logger.warn(
            s"[MonthlyReturnService][createBackendMonthlyReturn] Backend monthly return already exists for zReference [$zReference], taxYear [$taxYear], month [$month], submissionId [$submissionId], nilReturn [$nilReturn]"
          )
          AlreadyExists
      }

  def updateNilReturn(
    zReference: String,
    taxYear: String,
    month: Int,
    nilReturn: Boolean
  ): Future[UpdateNilReturnResult] =
    monthlyReturnRepository
      .updateNilReturn(zReference, taxYear, month, nilReturn)
      .map {
        case UpdateNilReturnRepositoryResult.NilReturnUpdated(monthlyReturn) =>
          logger.info(
            s"[MonthlyReturnService][updateNilReturn] Updated nilReturn to [$nilReturn] for zReference [$zReference], taxYear [$taxYear], month [$month]"
          )
          UpdateNilReturnResult.NilReturnUpdated(monthlyReturn)

        case UpdateNilReturnRepositoryResult.MonthlyReturnNotFound =>
          logger.info(
            s"[MonthlyReturnService][updateNilReturn] No monthly return found for zReference [$zReference], taxYear [$taxYear], month [$month]"
          )
          UpdateNilReturnResult.MonthlyReturnNotFound
      }
      .recoverWith { case NonFatal(exception) =>
        logger.error(
          s"[MonthlyReturnService][updateNilReturn] Failed to update nilReturn to [$nilReturn] for zReference [$zReference], taxYear [$taxYear], month [$month]",
          exception
        )
        Future.failed(exception)
      }

  def declare(zReference: String, taxYear: String, month: Int)(implicit
    hc: HeaderCarrier
  ): Future[DeclareMonthlyReturnResult] =
    if (!isWithinDeclarationPeriod) {
      logger.warn(
        s"[MonthlyReturnService][declare] Declaration period is closed for zReference [$zReference], taxYear [$taxYear], month [$month]"
      )
      Future.successful(DeclareMonthlyReturnResult.OutsideDeclarationPeriod)
    } else {
      monthlyReturnRepository
        .get(zReference, taxYear, month)
        .flatMap {
          case Some(monthlyReturn) =>
            returnsSubmissionConnector.declareMonthlyReturn(zReference, taxYear, month, monthlyReturn.nilReturn).map {
              case DeclareMonthlyReturnSubmissionResult.Declared =>
                logger.info(
                  s"[MonthlyReturnService][declare] Declared monthly return for zReference [$zReference], taxYear [$taxYear], month [$month]"
                )
                DeclareMonthlyReturnResult.Declared

              case DeclareMonthlyReturnSubmissionResult.AlreadyDeclared =>
                logger.warn(
                  s"[MonthlyReturnService][declare] Monthly return already declared in submission for zReference [$zReference], taxYear [$taxYear], month [$month]"
                )
                DeclareMonthlyReturnResult.AlreadyDeclared

              case DeclareMonthlyReturnSubmissionResult.MonthlyReturnNotFound =>
                logger.warn(
                  s"[MonthlyReturnService][declare] No monthly return found in submission for zReference [$zReference], taxYear [$taxYear], month [$month]"
                )
                DeclareMonthlyReturnResult.MonthlyReturnNotFound
            }

          case None =>
            logger.warn(
              s"[MonthlyReturnService][declare] No monthly return found for zReference [$zReference], taxYear [$taxYear], month [$month]"
            )
            Future.successful(DeclareMonthlyReturnResult.MonthlyReturnNotFound)
        }
        .recoverWith { case NonFatal(exception) =>
          logger.error(
            s"[MonthlyReturnService][declare] Failed to declare monthly return for zReference [$zReference], taxYear [$taxYear], month [$month]",
            exception
          )
          Future.failed(exception)
        }
    }

  def createFileUpload(
    zReference: String,
    taxYear: String,
    month: Int,
    reference: String
  ): Future[CreateFileUploadResult] =
    monthlyReturnRepository
      .createFileUpload(zReference, taxYear, month, reference)
      .map {
        case CreateFileUploadRepositoryResult.FileUploadCreated(monthlyReturn) =>
          logger.info(
            s"[MonthlyReturnService][createFileUpload] Created file upload for zReference [$zReference], taxYear [$taxYear], month [$month], upload reference [$reference]"
          )
          FileUploadCreated(monthlyReturn)

        case CreateFileUploadRepositoryResult.FileUploadAlreadyExists =>
          logger.warn(
            s"[MonthlyReturnService][createFileUpload] File upload already exists for zReference [$zReference], taxYear [$taxYear], month [$month], upload reference [$reference]"
          )
          FileUploadAlreadyExists

        case CreateFileUploadRepositoryResult.MonthlyReturnNotFound =>
          logger.warn(
            s"[MonthlyReturnService][createFileUpload] No monthly return found for zReference [$zReference], taxYear [$taxYear], month [$month], upload reference [$reference]"
          )
          MonthlyReturnNotFound
      }
      .recoverWith { case NonFatal(exception) =>
        logger.error(
          s"[MonthlyReturnService][createFileUpload] Failed to create file upload for zReference [$zReference], taxYear [$taxYear], month [$month], upload reference [$reference]",
          exception
        )
        Future.failed(exception)
      }

  def getFileUpload(
    zReference: String,
    taxYear: String,
    month: Int,
    reference: String
  ): Future[Option[FileUpload]] =
    monthlyReturnRepository
      .get(zReference, taxYear, month)
      .map(_.flatMap(_.getFileUpload(reference)))
      .map { maybeFileUpload =>
        maybeFileUpload match {
          case Some(_) =>
            logger.info(
              s"[MonthlyReturnService][getFileUpload] Found file upload for zReference [$zReference], taxYear [$taxYear], month [$month], upload reference [$reference]"
            )
          case None    =>
            logger.warn(
              s"[MonthlyReturnService][getFileUpload] No file upload found for zReference [$zReference], taxYear [$taxYear], month [$month], upload reference [$reference]"
            )
        }

        maybeFileUpload
      }
      .recoverWith { case NonFatal(exception) =>
        logger.error(
          s"[MonthlyReturnService][getFileUpload] Failed to get file upload for zReference [$zReference], taxYear [$taxYear], month [$month], upload reference [$reference]",
          exception
        )
        Future.failed(exception)
      }

  def deleteFileUpload(
    zReference: String,
    taxYear: String,
    month: Int,
    reference: String
  ): Future[Boolean] =
    monthlyReturnRepository
      .deleteFileUpload(zReference, taxYear, month, reference)
      .map { deleted =>
        if (deleted) {
          logger.info(
            s"[MonthlyReturnService][deleteFileUpload] Deleted file upload for zReference [$zReference], taxYear [$taxYear], month [$month], upload reference [$reference]"
          )
        } else {
          logger.warn(
            s"[MonthlyReturnService][deleteFileUpload] No file upload found to delete for zReference [$zReference], taxYear [$taxYear], month [$month], upload reference [$reference]"
          )
        }
        deleted
      }
      .recoverWith { case NonFatal(exception) =>
        logger.error(
          s"[MonthlyReturnService][deleteFileUpload] Failed to delete file upload for zReference [$zReference], taxYear [$taxYear], month [$month], upload reference [$reference]",
          exception
        )
        Future.failed(exception)
      }

  def updateFileUploadProcessingDetails(
    monthlyReturn: MonthlyReturn,
    reference: String,
    validation: FileUploadValidationResult,
    objectStoreFileLocation: Option[String],
    objectStoreFileErrorsLocation: Option[String]
  ): Future[Boolean] =
    monthlyReturnRepository.updateFileUploadProcessingDetails(
      zReference = monthlyReturn.zReference,
      taxYear = monthlyReturn.taxYear,
      month = monthlyReturn.month,
      reference = reference,
      validation = validation,
      objectStoreFileLocation = objectStoreFileLocation,
      objectStoreFileErrorsLocation = objectStoreFileErrorsLocation
    )

  def markUpscanExpired(
    monthlyReturn: MonthlyReturn,
    reference: String
  ): Future[Boolean] = {
    logger.info(
      s"[MonthlyReturnService][markUpscanExpired] Marking upscan as expired for zReference [${monthlyReturn.zReference}], taxYear [${monthlyReturn.taxYear}], month [${monthlyReturn.month}], upload reference [$reference]"
    )

    monthlyReturnRepository
      .markUpscanExpired(
        zReference = monthlyReturn.zReference,
        taxYear = monthlyReturn.taxYear,
        month = monthlyReturn.month,
        reference = reference
      )
      .map { updated =>
        if (updated) {
          logger.info(
            s"[MonthlyReturnService][markUpscanExpired] Marked upscan as expired for zReference [${monthlyReturn.zReference}], taxYear [${monthlyReturn.taxYear}], month [${monthlyReturn.month}], upload reference [$reference]"
          )
        } else {
          logger.warn(
            s"[MonthlyReturnService][markUpscanExpired] No UpscanSuccess file upload found to mark expired for zReference [${monthlyReturn.zReference}], taxYear [${monthlyReturn.taxYear}], month [${monthlyReturn.month}], upload reference [$reference]"
          )
        }
        updated
      }
      .recoverWith { case NonFatal(exception) =>
        logger.error(
          s"[MonthlyReturnService][markUpscanExpired] Failed to mark upscan as expired for zReference [${monthlyReturn.zReference}], taxYear [${monthlyReturn.taxYear}], month [${monthlyReturn.month}], upload reference [$reference]",
          exception
        )
        Future.failed(exception)
      }
  }

  private def isWithinDeclarationPeriod: Boolean = {
    val dayOfMonth                        = LocalDate.now(clock).getDayOfMonth
    val isOnOrAfterDeclarationPeriodStart = dayOfMonth >= appConfig.declarationPeriodStart
    val isOnOrBeforeDeclarationPeriodEnd  = dayOfMonth <= appConfig.declarationPeriodEnd

    isOnOrAfterDeclarationPeriodStart && isOnOrBeforeDeclarationPeriodEnd
  }

  private def declaredOn(monthlyReturn: JsValue): Option[Instant] =
    (monthlyReturn \ "declaredOn").asOpt[Instant]

}

sealed trait CreateMonthlyReturnResult

object CreateMonthlyReturnResult {
  final case class Created(submissionId: UUID) extends CreateMonthlyReturnResult
  case object AlreadyExists extends CreateMonthlyReturnResult
  case object OutsideDeclarationPeriod extends CreateMonthlyReturnResult
}

sealed trait CreateFileUploadResult

object CreateFileUploadResult {
  final case class FileUploadCreated(monthlyReturn: MonthlyReturn) extends CreateFileUploadResult
  case object FileUploadAlreadyExists extends CreateFileUploadResult
  case object MonthlyReturnNotFound extends CreateFileUploadResult
}

sealed trait UpdateNilReturnResult

object UpdateNilReturnResult {
  final case class NilReturnUpdated(monthlyReturn: MonthlyReturn) extends UpdateNilReturnResult
  case object MonthlyReturnNotFound extends UpdateNilReturnResult
}

sealed trait DeclareMonthlyReturnResult

object DeclareMonthlyReturnResult {
  case object Declared extends DeclareMonthlyReturnResult
  case object AlreadyDeclared extends DeclareMonthlyReturnResult
  case object MonthlyReturnNotFound extends DeclareMonthlyReturnResult
  case object OutsideDeclarationPeriod extends DeclareMonthlyReturnResult
}
