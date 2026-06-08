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
import uk.gov.hmrc.disareturnsbackend.models.{FileUpload, MonthlyReturn}
import uk.gov.hmrc.disareturnsbackend.repositories.MonthlyReturnRepository
import uk.gov.hmrc.disareturnsbackend.repositories.MonthlyReturnRepository.CreateFileUploadRepositoryResult
import uk.gov.hmrc.disareturnsbackend.services.CreateFileUploadResult.*
import uk.gov.hmrc.disareturnsbackend.services.CreateMonthlyReturnResult.{AlreadyExists, Created}

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class MonthlyReturnService @Inject() (
  monthlyReturnRepository: MonthlyReturnRepository,
  uuidGenerator: UuidGenerator
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
  ): Future[CreateMonthlyReturnResult] = {
    val submissionId = uuidGenerator.randomUuid()

    monthlyReturnRepository
      .create(zReference, taxYear, month, submissionId, nilReturn)
      .map {
        case Some(monthlyReturn) =>
          logger.info(
            s"[MonthlyReturnService][create] Created monthly return for zReference [$zReference], taxYear [$taxYear], month [$month], submissionId [$submissionId], nilReturn [$nilReturn]"
          )
          Created(monthlyReturn.submissionId)

        case None =>
          logger.warn(
            s"[MonthlyReturnService][create] Monthly return already exists for zReference [$zReference], taxYear [$taxYear], month [$month]"
          )
          AlreadyExists
      }
      .recoverWith { case NonFatal(exception) =>
        logger.error(
          s"[MonthlyReturnService][create] Failed to create monthly return for zReference [$zReference], taxYear [$taxYear], month [$month], submissionId [$submissionId], nilReturn [$nilReturn]",
          exception
        )
        Future.failed(exception)
      }
  }

  def updateNilReturn(
    zReference: String,
    taxYear: String,
    month: Int,
    nilReturn: Boolean
  ): Future[Option[MonthlyReturn]] =
    monthlyReturnRepository
      .updateNilReturn(zReference, taxYear, month, nilReturn)
      .map { maybeMonthlyReturn =>
        maybeMonthlyReturn match {
          case Some(_) =>
            logger.info(
              s"[MonthlyReturnService][updateNilReturn] Updated nilReturn to [$nilReturn] for zReference [$zReference], taxYear [$taxYear], month [$month]"
            )
          case None    =>
            logger.warn(
              s"[MonthlyReturnService][updateNilReturn] No monthly return found for zReference [$zReference], taxYear [$taxYear], month [$month]"
            )
        }

        maybeMonthlyReturn
      }
      .recoverWith { case NonFatal(exception) =>
        logger.error(
          s"[MonthlyReturnService][updateNilReturn] Failed to update nilReturn to [$nilReturn] for zReference [$zReference], taxYear [$taxYear], month [$month]",
          exception
        )
        Future.failed(exception)
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
            s"[MonthlyReturnService][createFileUpload] No writable monthly return found for zReference [$zReference], taxYear [$taxYear], month [$month], upload reference [$reference]"
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
      .getFileUpload(zReference, taxYear, month, reference)
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
}

sealed trait CreateMonthlyReturnResult

object CreateMonthlyReturnResult {
  final case class Created(submissionId: UUID) extends CreateMonthlyReturnResult
  case object AlreadyExists extends CreateMonthlyReturnResult
}

sealed trait CreateFileUploadResult

object CreateFileUploadResult {
  final case class FileUploadCreated(monthlyReturn: MonthlyReturn) extends CreateFileUploadResult
  case object FileUploadAlreadyExists extends CreateFileUploadResult
  case object MonthlyReturnNotFound extends CreateFileUploadResult
}
