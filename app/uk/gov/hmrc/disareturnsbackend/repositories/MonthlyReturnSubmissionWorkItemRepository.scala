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

import org.mongodb.scala.model.*
import uk.gov.hmrc.disareturnsbackend.config.AppConfig
import uk.gov.hmrc.disareturnsbackend.models.MonthlyReturnSubmissionWorkItem
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.MongoUtils.DuplicateKey
import uk.gov.hmrc.mongo.workitem.*

import java.time.{Clock, Duration, Instant}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

private object MonthlyReturnSubmissionWorkItemFields {
  val zReference = "item.zReference"
  val taxYear    = "item.taxYear"
  val month      = "item.month"
  val reference  = "item.reference"
}

@Singleton
class MonthlyReturnSubmissionWorkItemRepository @Inject() (
  clock: Clock,
  config: AppConfig,
  mongoComponent: MongoComponent
)(implicit ec: ExecutionContext)
    extends WorkItemRepository[MonthlyReturnSubmissionWorkItem](
      collectionName = "monthlyReturnSubmissionWorkItems",
      mongoComponent = mongoComponent,
      itemFormat = MonthlyReturnSubmissionWorkItem.format,
      workItemFields = WorkItemFields.default,
      extraIndexes = Seq(
        IndexModel(
          Indexes.ascending(
            MonthlyReturnSubmissionWorkItemFields.zReference,
            MonthlyReturnSubmissionWorkItemFields.taxYear,
            MonthlyReturnSubmissionWorkItemFields.month,
            MonthlyReturnSubmissionWorkItemFields.reference
          ),
          IndexOptions().name("monthlyReturnSubmissionWorkItemKeyIdx").unique(true)
        )
      )
    ) {

  override def now(): Instant                 = clock.instant()
  override val inProgressRetryAfter: Duration = config.monthlyReturnSubmissionJobInProgressRetryAfter

  def enqueue(item: MonthlyReturnSubmissionWorkItem): Future[Unit] =
    pushNew(item).map(_ => ()).recover { case DuplicateKey(_) => () }

  def deleteAll(): Future[Long] =
    collection.deleteMany(Filters.empty()).toFuture().map(_.getDeletedCount)
}
