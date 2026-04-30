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

package uk.gov.hmrc.disareturnsbackend.services

import play.api.Logging
import uk.gov.hmrc.disareturnsbackend.repositories.{
  CsvProcessingWorkItemRepository,
  FileUploadRepository
}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.disareturnsbackend.repositories.XlsxProcessingWorkItemRepository

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import java.util.Locale

@Singleton
class UpscanCallbackService @Inject() (
    csvProcessingWorkItemRepository: CsvProcessingWorkItemRepository,
    xlsxProcessingWorkItemRepository: XlsxProcessingWorkItemRepository,
    fileUploadRepository: FileUploadRepository
)(implicit ec: ExecutionContext)
    extends Logging {

  def handleCallback(
      filename: String
  )(implicit hc: HeaderCarrier): Future[Unit] =
    for {
      _ <- fileUploadRepository.create(filename)
      _ <- fileUploadRepository.markReadyForParsing(filename)
      _ <- enqueueByExtension(filename)
    } yield ()

  private def enqueueByExtension(filename: String): Future[Unit] =
    if (filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
      xlsxProcessingWorkItemRepository.enqueue(filename).map(_ => ())
    } else {
      csvProcessingWorkItemRepository.enqueue(filename).map(_ => ())
    }
}
