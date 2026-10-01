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

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.FileIO
import play.api.Logging
import play.api.libs.Files.TemporaryFileCreator
import uk.gov.hmrc.disareturnsbackend.converters.*
import uk.gov.hmrc.disareturnsbackend.connectors.*
import uk.gov.hmrc.disareturnsbackend.models.*
import uk.gov.hmrc.disareturnsbackend.utils.Constants.*
import uk.gov.hmrc.disareturnsbackend.utils.TempFileSupport
import uk.gov.hmrc.http.HeaderCarrier

import java.nio.file.Path
import javax.inject.*
import scala.concurrent.*

@Singleton
class MonthlyReturnSubmissionProcessingService @Inject() (
  actorSystem: ActorSystem,
  override protected val temporaryFileCreator: TemporaryFileCreator,
  csvConverter: MonthlyReturnCsvConverter,
  xlsxConverter: MonthlyReturnXlsxConverter,
  objectStoreConnector: ObjectStoreConnector,
  returnsSubmissionConnector: ReturnsSubmissionConnector,
  monthlyReturnService: MonthlyReturnService
)(implicit ec: ExecutionContext, materializer: Materializer)
    extends Logging
    with TempFileSupport {

  private val conversionExecutionContext: ExecutionContextExecutor =
    actorSystem.dispatchers.lookup("contexts.monthly-return-submission-file-upload-blocking")

  def process(item: MonthlyReturnSubmissionWorkItem): Future[Unit] = {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    logger.info(
      s"[MonthlyReturnSubmissionProcessingService][process] Starting transfer for zReference [${item.zReference}], taxYear [${item.taxYear}], month [${item.month}], upload reference [${item.reference}]"
    )
    monthlyReturnService.get(item.zReference, item.taxYear, item.month).flatMap {
      case None                =>
        logger.warn(
          s"[MonthlyReturnSubmissionProcessingService][process] Monthly return not found for zReference [${item.zReference}], taxYear [${item.taxYear}], month [${item.month}]"
        )
        Future.failed(new NoSuchElementException("Monthly return not found for submission transfer"))
      case Some(monthlyReturn) =>
        monthlyReturn.getFileUpload(item.reference) match {
          case Some(upload) if upload.status == FileUploadStatus.Submitted =>
            logger.info(
              s"[MonthlyReturnSubmissionProcessingService][process] Upload [${item.reference}] already submitted for zReference [${item.zReference}], taxYear [${item.taxYear}], month [${item.month}]"
            )
            Future.successful(())
          case Some(upload)
              if upload.status == FileUploadStatus.ValidationSuccess && upload.fileUploadDetails.exists(
                _.objectStoreFileLocation.isDefined
              ) =>
            val details = upload.fileUploadDetails.get
            transfer(item, details)
          case _                                                           =>
            logger.warn(
              s"[MonthlyReturnSubmissionProcessingService][process] Upload [${item.reference}] is not ready for submission for zReference [${item.zReference}], taxYear [${item.taxYear}], month [${item.month}]"
            )
            Future.failed(new IllegalStateException(s"Upload [${item.reference}] is not ready for submission"))
        }
    }
  }

  private def transfer(item: MonthlyReturnSubmissionWorkItem, details: FileUploadDetails)(implicit
    hc: HeaderCarrier
  ): Future[Unit] =
    withTempFile("monthly-return-upload-source") { input =>
      withTempFile("monthly-return-upload-ndjson") { output =>
        for {
          source  <- objectStoreConnector.getFile(item.reference)
          _       <- source.runWith(FileIO.toPath(input))
          _       <- Future(blocking(convert(input, output, details.fileMimeType)))(conversionExecutionContext)
          _       <- returnsSubmissionConnector.sendSubmission(
                       item.zReference,
                       item.taxYear,
                       item.month,
                       item.reference,
                       FileIO.fromPath(output)
                     )
          updated <- monthlyReturnService.markFileUploadSubmitted(
                       item.zReference,
                       item.taxYear,
                       item.month,
                       item.reference
                     )
          _       <- if (updated) {
                       Future.successful(())
                     } else {
                       monthlyReturnService.get(item.zReference, item.taxYear, item.month).flatMap {
                         case Some(monthlyReturn)
                             if monthlyReturn
                               .getFileUpload(item.reference)
                               .exists(
                                 _.status == FileUploadStatus.Submitted
                               ) =>
                           Future.successful(())
                         case _ =>
                           Future.failed(new IllegalStateException(s"Could not mark upload [${item.reference}] submitted"))
                       }
                     }
        } yield {
          logger.info(
            s"[MonthlyReturnSubmissionProcessingService][transfer] Submitted upload [${item.reference}] for zReference [${item.zReference}], taxYear [${item.taxYear}], month [${item.month}]"
          )
          ()
        }
      }
    }

  private def convert(input: Path, output: Path, mimeType: String): Unit =
    mimeType match {
      case CSV_MIME_TYPE  => csvConverter.convert(input, output)
      case XLSX_MIME_TYPE => xlsxConverter.convert(input, output)
      case other          => throw new IllegalArgumentException(s"Unsupported monthly return file type [$other]")
    }
}
