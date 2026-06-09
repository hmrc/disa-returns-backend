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

package uk.gov.hmrc.disareturnsbackend.repositories

import uk.gov.hmrc.disareturnsbackend.config.AppConfig
import uk.gov.hmrc.disareturnsbackend.models.FileUploadWorkItem
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.workitem.{WorkItem, WorkItemFields, WorkItemRepository}

import java.time.{Clock, Duration, Instant}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class FileUploadWorkItemRepository @Inject() (
  clock: Clock,
  config: AppConfig,
  mongoComponent: MongoComponent
)(implicit ec: ExecutionContext)
    extends WorkItemRepository[FileUploadWorkItem](
      collectionName = "fileUploadWorkItems",
      mongoComponent = mongoComponent,
      itemFormat = FileUploadWorkItem.format,
      workItemFields = WorkItemFields.default
    ) {

  override def now(): Instant =
    clock.instant()

  override val inProgressRetryAfter: Duration =
    config.fileUploadJobInProgressRetryAfter

  def enqueue(
    zReference: String,
    taxYear: String,
    month: Int,
    fileReference: String
  ): Future[WorkItem[FileUploadWorkItem]] =
    pushNew(FileUploadWorkItem(zReference, taxYear, month, fileReference))
}
