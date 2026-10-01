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
import play.api.http.Status.TOO_MANY_REQUESTS
import play.api.inject.ApplicationLifecycle
import uk.gov.hmrc.disareturnsbackend.config.AppConfig
import uk.gov.hmrc.disareturnsbackend.models.MonthlyReturnSubmissionWorkItem
import uk.gov.hmrc.disareturnsbackend.repositories.MonthlyReturnSubmissionWorkItemRepository
import uk.gov.hmrc.disareturnsbackend.services.MonthlyReturnSubmissionProcessingService
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.mongo.workitem.*

import java.time.Clock
import javax.inject.{Inject, Singleton}
import scala.concurrent.Future
import scala.util.control.NonFatal

@Singleton
class MonthlyReturnSubmissionWorkItemJob @Inject() (
  actorSystem: ActorSystem,
  clock: Clock,
  lifecycle: ApplicationLifecycle,
  appConfig: AppConfig,
  repository: MonthlyReturnSubmissionWorkItemRepository,
  processingService: MonthlyReturnSubmissionProcessingService
) extends BaseWorkItemJob[MonthlyReturnSubmissionWorkItem](
      actorSystem = actorSystem,
      clock = clock,
      lifecycle = lifecycle,
      workItemRepository = repository,
      dispatcherName = "contexts.monthly-return-submission-work-item",
      pollInterval = appConfig.monthlyReturnSubmissionJobPollInterval,
      failedRetryAfter = appConfig.monthlyReturnSubmissionJobFailedRetryAfter,
      workerCount = appConfig.monthlyReturnSubmissionJobWorkerCount
    ) {

  override protected val jobName: String = "MonthlyReturnSubmissionWorkItemJob"

  override protected def processWorkItem(
    workerId: Int,
    workItem: WorkItem[MonthlyReturnSubmissionWorkItem]
  ): Future[Boolean] = {
    logger.info(
      s"[$jobName][processWorkItem] Worker $workerId processing work item [${workItem.id}] for upload reference [${workItem.item.reference}]"
    )
    Future.unit
      .flatMap(_ => processingService.process(workItem.item))
      .flatMap { _ =>
        logger.info(s"[$jobName][processWorkItem] Worker $workerId completed work item [${workItem.id}]")
        markProcessingStatus(workItem, workerId, ProcessingStatus.Succeeded)
      }
      .recoverWith {
        case error @ UpstreamErrorResponse.Upstream4xxResponse(_) if error.statusCode != TOO_MANY_REQUESTS =>
          logger.error(
            s"[$jobName][processWorkItem] Non-retryable transfer response for work item [${workItem.id}]",
            error
          )
          markProcessingStatus(workItem, workerId, ProcessingStatus.PermanentlyFailed)
      }
      .recoverWith { case NonFatal(exception) =>
        logger.error(s"[$jobName][processWorkItem] Transfer failed for work item [${workItem.id}]", exception)
        markProcessingStatus(workItem, workerId, ProcessingStatus.Failed)
      }
  }

  private def markProcessingStatus(
    workItem: WorkItem[MonthlyReturnSubmissionWorkItem],
    workerId: Int,
    status: ProcessingStatus
  ): Future[Boolean] =
    repository.markAs(workItem.id, status).map { marked =>
      if (!marked) {
        logger.warn(s"[$jobName][markProcessingStatus] Worker $workerId could not mark [${workItem.id}] as [$status]")
      }
      true
    }
}
