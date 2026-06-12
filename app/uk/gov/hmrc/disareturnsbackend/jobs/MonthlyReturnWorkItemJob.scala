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

package uk.gov.hmrc.disareturnsbackend.jobs

import org.apache.pekko.actor.ActorSystem
import play.api.inject.ApplicationLifecycle
import uk.gov.hmrc.disareturnsbackend.config.AppConfig
import uk.gov.hmrc.disareturnsbackend.models.*
import uk.gov.hmrc.disareturnsbackend.repositories.MonthlyReturnFileUploadWorkItemRepository
import uk.gov.hmrc.disareturnsbackend.services.*
import uk.gov.hmrc.mongo.workitem.{ProcessingStatus, WorkItem}

import java.time.Clock
import javax.inject.{Inject, Singleton}
import scala.concurrent.Future
import scala.util.control.NonFatal

@Singleton
class MonthlyReturnWorkItemJob @Inject() (
  actorSystem: ActorSystem,
  clock: Clock,
  lifecycle: ApplicationLifecycle,
  appConfig: AppConfig,
  monthlyReturnFileUploadWorkItemRepository: MonthlyReturnFileUploadWorkItemRepository,
  monthlyReturnFileUploadProcessingService: MonthlyReturnFileUploadProcessingService,
  monthlyReturnService: MonthlyReturnService
) extends BaseWorkItemJob[MonthlyReturnFileUploadWorkItem](
      actorSystem = actorSystem,
      clock = clock,
      lifecycle = lifecycle,
      workItemRepository = monthlyReturnFileUploadWorkItemRepository,
      dispatcherName = "contexts.monthly-return-file-upload-work-item",
      pollInterval = appConfig.monthlyReturnFileUploadJobPollInterval
    ) {

  override protected val jobName: String = "MonthlyReturnWorkItemJob"

  override protected def processWorkItem(
    workerId: Int,
    workItem: WorkItem[MonthlyReturnFileUploadWorkItem]
  ): Future[Boolean] = {
    val item = workItem.item
    logger.info(
      s"[MonthlyReturnWorkItemJob][processWorkItem] Worker $workerId processing work item for " +
        s"zReference [${item.zReference}], taxYear [${item.taxYear}], month [${item.month}], " +
        s"upload reference [${item.reference}]"
    )

    monthlyReturnService
      .get(item.zReference, item.taxYear, item.month)
      .flatMap {
        case Some(monthlyReturn) =>
          monthlyReturnFileUploadProcessingService
            .process(monthlyReturn, item.reference)
            .flatMap {
              case _: FileUploadProcessingResult.Processed =>
                logger.info(
                  s"[MonthlyReturnWorkItemJob][processWorkItem] Worker $workerId processed file upload for " +
                    s"zReference [${item.zReference}], taxYear [${item.taxYear}], month [${item.month}] " +
                    s"upload reference [${item.reference}]"
                )
                markWorkItemAndContinue(workItem, ProcessingStatus.Succeeded, workerId, "processing succeeded")

              case result @ (FileUploadProcessingResult.FileUploadNotFound |
                  FileUploadProcessingResult.FileUploadNotReady | FileUploadProcessingResult.MonthlyReturnUpdateFailed |
                  _: FileUploadProcessingResult.UnsupportedMimeType) =>
                markWorkItemAndContinue(
                  workItem,
                  ProcessingStatus.PermanentlyFailed,
                  workerId,
                  s"processing returned [$result]"
                )
            }
            .recoverWith { case NonFatal(exception) =>
              logger.error(
                s"[MonthlyReturnWorkItemJob][processWorkItem] Worker $workerId failed to process file upload for " +
                  s"zReference [${item.zReference}], taxYear [${item.taxYear}], month [${item.month}] " +
                  s"upload reference [${item.reference}]",
                exception
              )
              markWorkItemAndContinue(workItem, ProcessingStatus.Failed, workerId, "processing failed")
            }
        case None                =>
          logger.error(
            s"[MonthlyReturnWorkItemJob][processWorkItem] Worker $workerId cannot find monthly return for " +
              s"zReference [${item.zReference}], taxYear [${item.taxYear}], month [${item.month}]"
          )
          markWorkItemAndContinue(workItem, ProcessingStatus.PermanentlyFailed, workerId, "monthly return was missing")
      }
  }

  private def markWorkItemAndContinue(
    workItem: WorkItem[MonthlyReturnFileUploadWorkItem],
    status: ProcessingStatus,
    workerId: Int,
    reason: String
  ): Future[Boolean] =
    monthlyReturnFileUploadWorkItemRepository
      .markAs(workItem.id, status)
      .map { marked =>
        if (!marked) {
          logger.warn(
            s"[MonthlyReturnWorkItemJob][markWorkItemAndContinue] Worker $workerId failed to mark work item " +
              s"[${workItem.id}] as [$status] after [$reason]"
          )
        }

        // Indicates a work item was pulled and handled/attempted.
        // It does not mean the file upload business processing succeeded.
        true
      }
}
