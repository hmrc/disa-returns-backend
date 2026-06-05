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
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}

import java.time.{Clock, Instant}
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
      domainFormat = MonthlyReturn.format,
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
        Codecs.playFormatSumCodecs(FileUploadFailureReason.format)
    ) {

  def get(zReference: String, taxYear: String, month: String): Future[Option[MonthlyReturn]] =
    collection
      .find(byKey(zReference, taxYear, month))
      .headOption()

  def upsert(monthlyReturn: MonthlyReturn): Future[Boolean] =
    replace(monthlyReturn.copy(lastUpdated = now()))

  def createFileUpload(
    zReference: String,
    taxYear: String,
    month: String,
    reference: String
  ): Future[Boolean] = {
    val createdOn = now()

    get(zReference, taxYear, month).flatMap { maybeMonthlyReturn =>
      val monthlyReturn = maybeMonthlyReturn.getOrElse(
        MonthlyReturn(
          zReference = zReference,
          taxYear = taxYear,
          month = month,
          fileUploads = Nil,
          lastUpdated = createdOn
        )
      )

      val updatedMonthlyReturn = monthlyReturn.createFileUpload(
        reference = reference,
        createdOn = createdOn
      )

      if (updatedMonthlyReturn == monthlyReturn) {
        Future.successful(false)
      } else {
        replace(updatedMonthlyReturn)
      }
    }
  }

  def completeFileUpload(
    zReference: String,
    taxYear: String,
    month: String,
    reference: String,
    status: FileUploadStatus,
    fileUploadDetails: Option[FileUploadDetails],
    downloadUrl: Option[String] = None,
    failureReason: Option[FileUploadFailureReason] = None,
    failureMessage: Option[String] = None
  ): Future[Boolean] = {
    val completedOn = now()

    get(zReference, taxYear, month).flatMap {
      case Some(monthlyReturn) =>
        val updatedMonthlyReturn = monthlyReturn.completeFileUpload(
          reference = reference,
          status = status,
          completedOn = completedOn,
          fileUploadDetails = fileUploadDetails,
          downloadUrl = downloadUrl,
          failureReason = failureReason,
          failureMessage = failureMessage
        )

        if (updatedMonthlyReturn == monthlyReturn) {
          Future.successful(false)
        } else {
          replace(updatedMonthlyReturn)
        }

      case None =>
        Future.successful(false)
    }
  }

  private def replace(monthlyReturn: MonthlyReturn): Future[Boolean] =
    collection
      .replaceOne(
        filter = byKey(monthlyReturn.zReference, monthlyReturn.taxYear, monthlyReturn.month),
        replacement = monthlyReturn,
        options = ReplaceOptions().upsert(true)
      )
      .toFuture()
      .map(_.wasAcknowledged())

  private def byKey(zReference: String, taxYear: String, month: String): Bson =
    Filters.and(
      Filters.equal("zReference", zReference),
      Filters.equal("taxYear", taxYear),
      Filters.equal("month", month)
    )

  private def now(): Instant = Instant.now(clock)
}
