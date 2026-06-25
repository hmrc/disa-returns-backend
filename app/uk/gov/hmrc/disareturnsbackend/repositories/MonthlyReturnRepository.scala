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

import org.bson.conversions.Bson
import org.mongodb.scala.model.*
import uk.gov.hmrc.disareturnsbackend.config.AppConfig
import uk.gov.hmrc.disareturnsbackend.models.*
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.MongoUtils.DuplicateKey
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}

import java.time.{Clock, Instant}
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MonthlyReturnRepository @Inject() (
  mongoComponent: MongoComponent,
  appConfig: AppConfig,
  clock: Clock
)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[MonthlyReturn](
      mongoComponent = mongoComponent,
      collectionName = "monthlyReturns",
      domainFormat = MonthlyReturn.mongoFormat,
      indexes = Seq(
        IndexModel(
          Indexes.ascending("lastUpdated"),
          IndexOptions()
            .name("monthlyReturnsTtl")
            .expireAfter(appConfig.monthlyReturnTimeToLiveInDays, TimeUnit.DAYS)
        ),
        IndexModel(
          Indexes.ascending("zReference", "taxYear", "month"),
          IndexOptions()
            .name("monthlyReturnKeyIdx")
            .unique(true)
        )
      ),
      replaceIndexes = true,
      extraCodecs = Codecs.playFormatSumCodecs(FileUploadStatus.format) ++
        Codecs.playFormatSumCodecs(FileUploadFailureReason.format) ++
        Codecs.playFormatSumCodecs(FileUploadValidationStatus.format)
    ) {

  def get(zReference: String, taxYear: String, month: Int): Future[Option[MonthlyReturn]] =
    collection
      .find(byKey(zReference, taxYear, month))
      .headOption()

  def deleteAll(): Future[Long] =
    collection
      .deleteMany(Filters.empty())
      .toFuture()
      .map(_.getDeletedCount)

  def create(
    zReference: String,
    taxYear: String,
    month: Int,
    submissionId: UUID,
    nilReturn: Boolean
  ): Future[Option[MonthlyReturn]] = {
    val createdOn     = now()
    val monthlyReturn = MonthlyReturn(
      zReference = zReference,
      submissionId = submissionId,
      taxYear = taxYear,
      month = month,
      createdOn = createdOn,
      nilReturn = nilReturn,
      fileUploads = Nil,
      lastUpdated = createdOn
    )

    collection
      .insertOne(monthlyReturn)
      .toFuture()
      .map(result => Option.when(result.wasAcknowledged())(monthlyReturn))
      .recover { case DuplicateKey(_) => None }
  }

  def upsert(monthlyReturn: MonthlyReturn): Future[Boolean] =
    replace(monthlyReturn.copy(lastUpdated = now()))

  private def replace(monthlyReturn: MonthlyReturn): Future[Boolean] =
    collection
      .replaceOne(
        filter = byKey(monthlyReturn.zReference, monthlyReturn.taxYear, monthlyReturn.month),
        replacement = monthlyReturn,
        options = ReplaceOptions().upsert(true)
      )
      .toFuture()
      .map(_.wasAcknowledged())

  private def byKey(zReference: String, taxYear: String, month: Int): Bson =
    Filters.and(
      Filters.equal("zReference", zReference),
      Filters.equal("taxYear", taxYear),
      Filters.equal("month", month)
    )

  // Remove the microseconds to avoid String comparison mismatches
  private def now(): Instant = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS)
}
