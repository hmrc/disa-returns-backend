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
import uk.gov.hmrc.disareturnsbackend.models.FileUploadWorkItem
import uk.gov.hmrc.disareturnsbackend.repositories.FileUploadWorkItemRepository
import uk.gov.hmrc.mongo.workitem.WorkItem

import java.time.Clock
import javax.inject.{Inject, Singleton}
import scala.concurrent.Future

@Singleton
class FileUploadWorkItemJob @Inject() (
  actorSystem: ActorSystem,
  clock: Clock,
  lifecycle: ApplicationLifecycle,
  appConfig: AppConfig,
  fileUploadValidationWorkItemRepository: FileUploadWorkItemRepository
) extends WorkItemJob[FileUploadWorkItem](
      actorSystem = actorSystem,
      clock = clock,
      lifecycle = lifecycle,
      workItemRepository = fileUploadValidationWorkItemRepository,
      dispatcherName = "contexts.file-upload-work-item",
      pollInterval = appConfig.fileUploadJobPollInterval
    ) {

  override protected val jobName: String = "FileUploadWorkItemJob"

  override protected def processWorkItem(
    workerId: Int,
    workItem: WorkItem[FileUploadWorkItem]
  ): Future[Boolean] = {
    val item = workItem.item
    logger.info(
      s"[FileUploadWorkItemJob][processWorkItem] Worker $workerId processing work item for " +
        s"zReference [${item.zReference}], taxYear [${item.taxYear}], month [${item.month}], " +
        s"upload reference [${item.reference}]"
    )
    Future.successful(true)
  }
}
