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

import org.apache.pekko.stream.Materializer
import play.api.libs.Files.TemporaryFileCreator
import uk.gov.hmrc.disareturnsbackend.connectors.*
import uk.gov.hmrc.disareturnsbackend.models.*
import uk.gov.hmrc.disareturnsbackend.repositories.MonthlyReturnRepository
import uk.gov.hmrc.disareturnsbackend.validators.fileupload.monthly.*

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

trait MonthlyReturnFileUploadProcessingService {
  def process(monthlyReturn: MonthlyReturn, fileUploadReference: String): Future[FileUploadProcessingResult]
}

@Singleton
class MonthlyReturnFileUploadProcessingServiceImpl @Inject() (
  override protected val temporaryFileCreator: TemporaryFileCreator,
  upscanConnector: UpscanConnector,
  monthlyReturnFileValidatorSelector: MonthlyReturnFileValidatorSelector,
  objectStoreConnector: ObjectStoreConnector,
  monthlyReturnRepository: MonthlyReturnRepository
)(implicit ec: ExecutionContext, materializer: Materializer)
    extends BaseFileUploadProcessingService[MonthlyReturn, MonthlyReturnFileUploadValidationContext](
      temporaryFileCreator = temporaryFileCreator,
      upscanConnector = upscanConnector,
      fileValidatorSelector = monthlyReturnFileValidatorSelector,
      objectStoreConnector = objectStoreConnector
    )
    with MonthlyReturnFileUploadProcessingService {

  override protected val serviceName: String =
    "MonthlyReturnFileUploadProcessingService"

  override protected def getFileUpload(monthlyReturn: MonthlyReturn, fileUploadReference: String): Option[FileUpload] =
    monthlyReturn.getFileUpload(fileUploadReference)

  override protected def validationContext(monthlyReturn: MonthlyReturn): MonthlyReturnFileUploadValidationContext =
    MonthlyReturnFileUploadValidationContext(
      taxYear = monthlyReturn.taxYear,
      month = monthlyReturn.month
    )

  override protected def updateFileUploadProcessingDetails(
    monthlyReturn: MonthlyReturn,
    fileUploadReference: String,
    validation: FileUploadValidationResult,
    objectStoreFileLocation: Option[String],
    objectStoreFileErrorsLocation: Option[String]
  ): Future[Boolean] =
    monthlyReturnRepository.updateFileUploadProcessingDetails(
      zReference = monthlyReturn.zReference,
      taxYear = monthlyReturn.taxYear,
      month = monthlyReturn.month,
      reference = fileUploadReference,
      validation = validation,
      objectStoreFileLocation = objectStoreFileLocation,
      objectStoreFileErrorsLocation = objectStoreFileErrorsLocation
    )

  override protected def describeReturn(monthlyReturn: MonthlyReturn): String =
    s"monthly return zReference [${monthlyReturn.zReference}], taxYear [${monthlyReturn.taxYear}], month [${monthlyReturn.month}]"
}
