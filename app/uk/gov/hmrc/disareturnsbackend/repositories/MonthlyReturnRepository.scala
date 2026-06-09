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
import uk.gov.hmrc.disareturnsbackend.repositories.MonthlyReturnRepository.CreateFileUploadRepositoryResult
import uk.gov.hmrc.disareturnsbackend.repositories.MonthlyReturnRepository.CreateFileUploadRepositoryResult.*
import uk.gov.hmrc.disareturnsbackend.repositories.MonthlyReturnRepository.DeclareMonthlyReturnRepositoryResult
import uk.gov.hmrc.disareturnsbackend.repositories.MonthlyReturnRepository.UpdateNilReturnRepositoryResult
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
        Codecs.playFormatSumCodecs(FileUploadFailureReason.format)
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

  def updateNilReturn(
    zReference: String,
    taxYear: String,
    month: Int,
    nilReturn: Boolean
  ): Future[UpdateNilReturnRepositoryResult] = {
    val updatedOn = now()

    get(zReference, taxYear, month).flatMap {
      case Some(monthlyReturn) if monthlyReturn.hasDeclaration =>
        Future.successful(UpdateNilReturnRepositoryResult.MonthlyReturnAlreadyDeclared)

      case Some(monthlyReturn) =>
        val updatedMonthlyReturn = monthlyReturn.updateNilReturn(nilReturn, updatedOn)

        if (updatedMonthlyReturn == monthlyReturn) {
          Future.successful(UpdateNilReturnRepositoryResult.NilReturnUpdated(monthlyReturn))
        } else {
          replace(updatedMonthlyReturn).map(_ => UpdateNilReturnRepositoryResult.NilReturnUpdated(updatedMonthlyReturn))
        }

      case None =>
        Future.successful(UpdateNilReturnRepositoryResult.MonthlyReturnNotFound)
    }
  }

  def declare(zReference: String, taxYear: String, month: Int): Future[DeclareMonthlyReturnRepositoryResult] = {
    val declaredOn = now()

    get(zReference, taxYear, month).flatMap {
      case Some(monthlyReturn) if monthlyReturn.hasDeclaration =>
        Future.successful(DeclareMonthlyReturnRepositoryResult.MonthlyReturnAlreadyDeclared)

      case Some(monthlyReturn) =>
        val updatedMonthlyReturn = monthlyReturn.declare(declaredOn)

        replace(updatedMonthlyReturn).map(_ => DeclareMonthlyReturnRepositoryResult.MonthlyReturnDeclared)

      case None =>
        Future.successful(DeclareMonthlyReturnRepositoryResult.MonthlyReturnNotFound)
    }
  }

  def createFileUpload(
    zReference: String,
    taxYear: String,
    month: Int,
    reference: String
  ): Future[CreateFileUploadRepositoryResult] = {
    val createdOn = now()

    get(zReference, taxYear, month).flatMap {
      case Some(monthlyReturn) if monthlyReturn.nilReturn =>
        Future.successful(MonthlyReturnNotFound)

      case Some(monthlyReturn) if monthlyReturn.hasDeclaration =>
        Future.successful(MonthlyReturnAlreadyDeclared)

      case Some(monthlyReturn) if monthlyReturn.fileUploads.exists(_.reference == reference) =>
        Future.successful(FileUploadAlreadyExists)

      case Some(monthlyReturn) =>
        val updatedMonthlyReturn = monthlyReturn.createFileUpload(
          reference = reference,
          createdOn = createdOn
        )

        replace(updatedMonthlyReturn).map(_ => FileUploadCreated(updatedMonthlyReturn))

      case None =>
        Future.successful(MonthlyReturnNotFound)
    }
  }

  def getFileUpload(
    zReference: String,
    taxYear: String,
    month: Int,
    reference: String
  ): Future[Option[FileUpload]] =
    get(zReference, taxYear, month).map(_.flatMap(_.fileUploads.find(_.reference == reference)))

  def deleteFileUpload(
    zReference: String,
    taxYear: String,
    month: Int,
    reference: String
  ): Future[Boolean] = {
    val updatedOn = now()

    get(zReference, taxYear, month).flatMap {
      case Some(monthlyReturn) =>
        val updatedMonthlyReturn = monthlyReturn.deleteFileUpload(reference, updatedOn)

        if (updatedMonthlyReturn == monthlyReturn) {
          Future.successful(false)
        } else {
          replace(updatedMonthlyReturn)
        }

      case None =>
        Future.successful(false)
    }
  }

  def completeUpscan(
    zReference: String,
    taxYear: String,
    month: Int,
    reference: String,
    status: FileUploadStatus,
    fileUploadDetails: Option[FileUploadDetails],
    failureReason: Option[FileUploadFailureReason] = None,
    failureMessage: Option[String] = None
  ): Future[Boolean] = {
    val upscanCompletedOn = now()

    get(zReference, taxYear, month).flatMap {
      case Some(monthlyReturn) if monthlyReturn.nilReturn =>
        Future.successful(false)

      case Some(monthlyReturn) =>
        val updatedMonthlyReturn = monthlyReturn.completeUpscan(
          reference = reference,
          status = status,
          upscanCompletedOn = upscanCompletedOn,
          fileUploadDetails = fileUploadDetails,
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

  private def byKey(zReference: String, taxYear: String, month: Int): Bson =
    Filters.and(
      Filters.equal("zReference", zReference),
      Filters.equal("taxYear", taxYear),
      Filters.equal("month", month)
    )

  private def now(): Instant = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS)
}

object MonthlyReturnRepository {

  sealed trait CreateFileUploadRepositoryResult

  object CreateFileUploadRepositoryResult {
    final case class FileUploadCreated(monthlyReturn: MonthlyReturn) extends CreateFileUploadRepositoryResult
    case object FileUploadAlreadyExists extends CreateFileUploadRepositoryResult
    case object MonthlyReturnAlreadyDeclared extends CreateFileUploadRepositoryResult
    case object MonthlyReturnNotFound extends CreateFileUploadRepositoryResult
  }

  sealed trait UpdateNilReturnRepositoryResult

  object UpdateNilReturnRepositoryResult {
    final case class NilReturnUpdated(monthlyReturn: MonthlyReturn) extends UpdateNilReturnRepositoryResult
    case object MonthlyReturnAlreadyDeclared extends UpdateNilReturnRepositoryResult
    case object MonthlyReturnNotFound extends UpdateNilReturnRepositoryResult
  }

  sealed trait DeclareMonthlyReturnRepositoryResult

  object DeclareMonthlyReturnRepositoryResult {
    case object MonthlyReturnDeclared extends DeclareMonthlyReturnRepositoryResult
    case object MonthlyReturnAlreadyDeclared extends DeclareMonthlyReturnRepositoryResult
    case object MonthlyReturnNotFound extends DeclareMonthlyReturnRepositoryResult
  }
}
