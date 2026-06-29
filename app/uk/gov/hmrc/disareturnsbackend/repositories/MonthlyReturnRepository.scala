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
import uk.gov.hmrc.disareturnsbackend.repositories.CreateFileUploadRepositoryResult.*
import uk.gov.hmrc.disareturnsbackend.repositories.UpdateNilReturnRepositoryResult.*
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.MongoUtils.DuplicateKey
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}

import java.time.{Clock, Instant}
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

private val failureMessageField                                  = "failureMessage"
private val failureReasonField                                   = "failureReason"
private val fileUploadDetailsField                               = "fileUploadDetails"
private val fileUploadsField                                     = "fileUploads"
private val fileUploadsNonEmptyField                             = "fileUploads.0"
private val lastUpdatedField                                     = "lastUpdated"
private val matchingFileUploadDetailsField                       = "fileUploads.$.fileUploadDetails"
private val matchingFileUploadField                              = "fileUploads.$"
private val matchingFileUploadFailureMessageField                = "fileUploads.$.failureMessage"
private val matchingFileUploadFailureReasonField                 = "fileUploads.$.failureReason"
private val matchingFileUploadObjectStoreFileErrorsLocationField =
  "fileUploads.$.fileUploadDetails.objectStoreFileErrorsLocation"
private val matchingFileUploadObjectStoreFileLocationField       =
  "fileUploads.$.fileUploadDetails.objectStoreFileLocation"
private val matchingFileUploadStatusField                        = "fileUploads.$.status"
private val matchingFileUploadValidationField                    = "fileUploads.$.fileUploadDetails.validation"
private val monthField                                           = "month"
private val nilReturnField                                       = "nilReturn"
private val referenceField                                       = "reference"
private val statusField                                          = "status"
private val taxYearField                                         = "taxYear"
private val zReferenceField                                      = "zReference"

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
          Indexes.ascending(lastUpdatedField),
          IndexOptions()
            .name("monthlyReturnsTtl")
            .expireAfter(appConfig.monthlyReturnTimeToLiveInDays, TimeUnit.DAYS)
        ),
        IndexModel(
          Indexes.ascending(zReferenceField, taxYearField, monthField),
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

  def completeUpscan(
    zReference: String,
    taxYear: String,
    month: Int,
    reference: String,
    status: FileUploadStatus,
    fileUploadDetails: Option[FileUploadDetails],
    failureReason: Option[FileUploadFailureReason] = None,
    failureMessage: Option[String] = None
  ): Future[Boolean] =
    get(zReference, taxYear, month).flatMap {
      case Some(monthlyReturn) if monthlyReturn.canCompleteUpscan(reference) =>
        val completedOn               = now()
        val isNotNilReturn            = Filters.equal(nilReturnField, false)
        val matchingCreatedFileUpload = Filters.elemMatch(
          fileUploadsField,
          Filters.and(
            Filters.equal(referenceField, reference),
            Filters.equal(statusField, FileUploadStatus.Created)
          )
        )
        val filter                    = Filters.and(
          byKey(zReference, taxYear, month),
          isNotNilReturn,
          matchingCreatedFileUpload
        )
        val fileUploadDetailsUpdate   = fileUploadDetails
          .map(details =>
            Updates.set(
              matchingFileUploadDetailsField,
              fileUploadDetailsBson(details.copy(upscanCompletedOn = Some(completedOn)))
            )
          )
          .getOrElse(Updates.unset(matchingFileUploadDetailsField))
        val failureReasonUpdate       = failureReason
          .map(reason => Updates.set(matchingFileUploadFailureReasonField, reason))
          .getOrElse(Updates.unset(matchingFileUploadFailureReasonField))
        val failureMessageUpdate      = failureMessage
          .map(message => Updates.set(matchingFileUploadFailureMessageField, message))
          .getOrElse(Updates.unset(matchingFileUploadFailureMessageField))

        collection
          .updateOne(
            filter = filter,
            update = Updates.combine(
              Updates.set(matchingFileUploadStatusField, status),
              fileUploadDetailsUpdate,
              failureReasonUpdate,
              failureMessageUpdate,
              Updates.set(lastUpdatedField, completedOn)
            )
          )
          .toFuture()
          .map(_.getModifiedCount == 1)

      case _ =>
        Future.successful(false)
    }

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

  def createFileUpload(
    zReference: String,
    taxYear: String,
    month: Int,
    reference: String
  ): Future[CreateFileUploadRepositoryResult] = {
    val createdOn                = now()
    val fileUpload               = FileUpload(
      reference = reference,
      status = FileUploadStatus.Created,
      createdOn = createdOn
    )
    val isNotNilReturn           = Filters.equal(nilReturnField, false)
    val hasNoDuplicateFileUpload =
      Filters.not(Filters.elemMatch(fileUploadsField, Filters.equal(referenceField, reference)))

    val filter = Filters.and(
      byKey(zReference, taxYear, month),
      isNotNilReturn,
      hasNoDuplicateFileUpload
    )

    collection
      .updateOne(
        filter = filter,
        update = Updates.combine(
          Updates.push(fileUploadsField, fileUploadBson(fileUpload)),
          Updates.set(lastUpdatedField, createdOn)
        )
      )
      .toFuture()
      .flatMap { result =>
        get(zReference, taxYear, month).map {
          case Some(monthlyReturn) if result.getModifiedCount == 1                               =>
            FileUploadCreated(monthlyReturn)
          case Some(monthlyReturn) if monthlyReturn.nilReturn                                    =>
            CreateFileUploadRepositoryResult.MonthlyReturnNotFound
          case Some(monthlyReturn) if monthlyReturn.fileUploads.exists(_.reference == reference) =>
            FileUploadAlreadyExists
          case _                                                                                 =>
            CreateFileUploadRepositoryResult.MonthlyReturnNotFound
        }
      }
  }

  def deleteAll(): Future[Long] =
    collection
      .deleteMany(Filters.empty())
      .toFuture()
      .map(_.getDeletedCount)

  def deleteFileUpload(
    zReference: String,
    taxYear: String,
    month: Int,
    reference: String
  ): Future[Boolean] = {
    val matchingFileUpload = Filters.elemMatch(fileUploadsField, Filters.equal(referenceField, reference))
    val filter             = Filters.and(
      byKey(zReference, taxYear, month),
      matchingFileUpload
    )

    val removeFileUploadUpdate = Updates.combine(
      Updates.pull(fileUploadsField, Filters.equal(referenceField, reference)),
      Updates.set(lastUpdatedField, now())
    )

    collection
      .updateOne(
        filter = filter,
        update = removeFileUploadUpdate
      )
      .toFuture()
      .map(_.getModifiedCount == 1)
  }

  def get(zReference: String, taxYear: String, month: Int): Future[Option[MonthlyReturn]] =
    collection
      .find(byKey(zReference, taxYear, month))
      .headOption()

  def markUpscanExpired(
    zReference: String,
    taxYear: String,
    month: Int,
    reference: String
  ): Future[Boolean] = {

    val updatedOn = now()

    val matchingUpscanSuccessFileUpload = Filters.elemMatch(
      fileUploadsField,
      Filters.and(
        Filters.equal(referenceField, reference),
        Filters.equal(statusField, FileUploadStatus.UpscanSuccess)
      )
    )

    val filter = Filters.and(
      byKey(zReference, taxYear, month),
      matchingUpscanSuccessFileUpload
    )

    val updateFileUploadToUpscanExpired = Updates.combine(
      Updates.set(matchingFileUploadStatusField, FileUploadStatus.UpscanExpired),
      Updates.set(lastUpdatedField, updatedOn)
    )

    collection
      .updateOne(
        filter = filter,
        update = updateFileUploadToUpscanExpired
      )
      .toFuture()
      .map(_.getModifiedCount == 1)
  }

  def updateFileUploadProcessingDetails(
    zReference: String,
    taxYear: String,
    month: Int,
    reference: String,
    validation: FileUploadValidationResult,
    objectStoreFileLocation: Option[String],
    objectStoreFileErrorsLocation: Option[String]
  ): Future[Boolean] =
    get(zReference, taxYear, month).flatMap {
      case Some(monthlyReturn) if monthlyReturn.getFileUpload(reference).exists(_.hasFileUploadDetails) =>
        val updatedOn        = now()
        val validationStatus = validation.status match {
          case FileUploadValidationStatus.ValidationSuccess => FileUploadStatus.ValidationSuccess
          case FileUploadValidationStatus.ValidationFailed  => FileUploadStatus.ValidationFailure
          case FileUploadValidationStatus.InvalidFile       => FileUploadStatus.ValidationFailure
        }

        val matchingUpscanSuccessFileUploadWithDetails = Filters.elemMatch(
          fileUploadsField,
          Filters.and(
            Filters.equal(referenceField, reference),
            Filters.equal(statusField, FileUploadStatus.UpscanSuccess),
            Filters.exists(fileUploadDetailsField)
          )
        )
        val filter                                     = Filters.and(
          byKey(zReference, taxYear, month),
          matchingUpscanSuccessFileUploadWithDetails
        )
        val updateFileUploadProcessingDetails          = Updates.combine(
          Updates.set(matchingFileUploadStatusField, validationStatus),
          setOrUnsetOptionField(matchingFileUploadObjectStoreFileLocationField, objectStoreFileLocation),
          setOrUnsetOptionField(matchingFileUploadObjectStoreFileErrorsLocationField, objectStoreFileErrorsLocation),
          Updates.set(matchingFileUploadValidationField, validationBson(validation)),
          Updates.set(lastUpdatedField, updatedOn)
        )

        collection
          .updateOne(
            filter = filter,
            update = updateFileUploadProcessingDetails
          )
          .toFuture()
          .map(_.getModifiedCount == 1)

      case _ =>
        Future.successful(false)
    }

  def updateNilReturn(
    zReference: String,
    taxYear: String,
    month: Int,
    nilReturn: Boolean
  ): Future[UpdateNilReturnRepositoryResult] = {
    val updatedOn = now()

    val hasFileUploads = Filters.exists(fileUploadsNonEmptyField)
    val isNilReturn    = Filters.equal(nilReturnField, true)
    val isNotNilReturn = Filters.equal(nilReturnField, false)

    val filter =
      if (nilReturn) {
        Filters.and(
          byKey(zReference, taxYear, month),
          Filters.or(isNotNilReturn, hasFileUploads)
        )
      } else {
        Filters.and(
          byKey(zReference, taxYear, month),
          isNilReturn
        )
      }

    val lastUpdatedOn = Updates.set(lastUpdatedField, updatedOn)

    val update =
      if (nilReturn) {
        val setNilReturnTrueAndClearFileUploads = Updates.combine(
          Updates.set(nilReturnField, true),
          Updates.set(fileUploadsField, List.empty[FileUpload]),
          lastUpdatedOn
        )
        setNilReturnTrueAndClearFileUploads
      } else {
        val setNilReturnFalse = Updates.combine(
          Updates.set(nilReturnField, false),
          lastUpdatedOn
        )
        setNilReturnFalse
      }

    collection
      .updateOne(
        filter = filter,
        update = update
      )
      .toFuture()
      .flatMap(_ => get(zReference, taxYear, month))
      .map {
        case Some(monthlyReturn) =>
          NilReturnUpdated(monthlyReturn)
        case None                => UpdateNilReturnRepositoryResult.MonthlyReturnNotFound
      }
  }

  private def byKey(zReference: String, taxYear: String, month: Int): Bson =
    Filters.and(
      Filters.equal(zReferenceField, zReference),
      Filters.equal(taxYearField, taxYear),
      Filters.equal(monthField, month)
    )

  private def fileUploadBson(fileUpload: FileUpload) =
    Codecs.toBson(fileUpload)(FileUpload.mongoFormat)

  private def fileUploadDetailsBson(fileUploadDetails: FileUploadDetails) =
    Codecs.toBson(fileUploadDetails)(FileUploadDetails.mongoFormat)

  // Remove the microseconds to avoid String comparison mismatches
  private def now(): Instant = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS)

  private def setOrUnsetOptionField(field: String, value: Option[String]): Bson =
    value.map(Updates.set(field, _)).getOrElse(Updates.unset(field))

  private def validationBson(validation: FileUploadValidationResult) =
    Codecs.toBson(validation)(FileUploadValidationResult.format)
}

sealed trait CreateFileUploadRepositoryResult

object CreateFileUploadRepositoryResult {
  final case class FileUploadCreated(monthlyReturn: MonthlyReturn) extends CreateFileUploadRepositoryResult
  case object FileUploadAlreadyExists extends CreateFileUploadRepositoryResult
  case object MonthlyReturnNotFound extends CreateFileUploadRepositoryResult
}

sealed trait UpdateNilReturnRepositoryResult

object UpdateNilReturnRepositoryResult {
  final case class NilReturnUpdated(monthlyReturn: MonthlyReturn) extends UpdateNilReturnRepositoryResult
  case object MonthlyReturnNotFound extends UpdateNilReturnRepositoryResult
}
