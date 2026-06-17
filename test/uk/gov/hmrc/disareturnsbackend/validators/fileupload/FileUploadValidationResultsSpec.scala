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

package uk.gov.hmrc.disareturnsbackend.validators.fileupload

import base.SpecBase
import uk.gov.hmrc.disareturnsbackend.models.{FileUploadValidationError, FileUploadValidationStatus}

class FileUploadValidationResultsSpec extends SpecBase {

  private val validationErrorCode = "E001"

  "FileUploadValidationResults" - {

    "must return InvalidFile with empty inline errors" in {
      val result = FileUploadValidationResults.invalidFile

      result.validation.status mustBe FileUploadValidationStatus.InvalidFile
      result.validation.inlineErrors mustBe Nil
    }

    "must return ValidationSuccess with empty inline errors" in {
      val result = FileUploadValidationResults.success(rowsValidated = 1)

      result.validation.status mustBe FileUploadValidationStatus.ValidationSuccess
      result.validation.inlineErrors mustBe Nil
    }

    "must include supplied inline errors when validation failed" in {
      val inlineErrors = List(FileUploadValidationError(rowNumber = 1, errorCodes = List(validationErrorCode)))

      val result = FileUploadValidationResults.failed(
        rowsValidated = 1,
        validationErrors = 1,
        errorFileWritten = true,
        inlineErrors = inlineErrors
      )

      result.validation.status mustBe FileUploadValidationStatus.ValidationFailed
      result.validation.inlineErrors mustBe inlineErrors
    }
  }
}
