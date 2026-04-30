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

import org.mongodb.scala.model.{Filters, IndexModel, IndexOptions, Indexes, ReplaceOptions}
import uk.gov.hmrc.disareturnsbackend.models.{FileUpload, FileUploadParseError, FileUploadStatus}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class FileUploadRepository @Inject() (
    mongoComponent: MongoComponent
)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[FileUpload](
      mongoComponent = mongoComponent,
      collectionName = "fileUploads",
      domainFormat = FileUpload.format,
      indexes = Seq(
        IndexModel(
          Indexes.ascending("filename"),
          IndexOptions().name("fileUploads_filename_idx").unique(true).background(true)
        )
      )
    ) {

  def create(filename: String): Future[Unit] =
    collection
      .replaceOne(
        Filters.equal("filename", filename),
        FileUpload(filename, FileUploadStatus.UpscanSucceeded),
        ReplaceOptions().upsert(true)
      )
      .toFuture()
      .map(_ => ())

  def markReadyForParsing(filename: String): Future[Unit] =
    replaceStatus(filename, FileUploadStatus.ReadyForParsing)

  def markParseSucceeded(filename: String): Future[Unit] =
    replaceStatus(filename, FileUploadStatus.ParseSucceeded)

  def markParseFailed(filename: String, parseErrors: Seq[FileUploadParseError]): Future[Unit] =
    collection
      .replaceOne(
        Filters.equal("filename", filename),
        FileUpload(filename, FileUploadStatus.ParseFailed, parseErrors),
        ReplaceOptions().upsert(true)
      )
      .toFuture()
      .map(_ => ())

  private def replaceStatus(filename: String, status: FileUploadStatus): Future[Unit] =
    collection
      .replaceOne(
        Filters.equal("filename", filename),
        FileUpload(filename, status),
        ReplaceOptions().upsert(true)
      )
      .toFuture()
      .map(_ => ())
}
