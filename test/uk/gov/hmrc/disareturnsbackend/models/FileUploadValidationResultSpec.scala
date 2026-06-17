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

package uk.gov.hmrc.disareturnsbackend.models

import base.SpecBase
import play.api.libs.json.{JsError, JsString, Json}

class FileUploadValidationResultSpec extends SpecBase {

  "FileUploadValidationStatus format" - {

    Seq[(FileUploadValidationStatus, String)](
      FileUploadValidationStatus.ValidationSuccess -> "ValidationSuccess",
      FileUploadValidationStatus.ValidationFailed  -> "ValidationFailed",
      FileUploadValidationStatus.InvalidFile       -> "InvalidFile"
    ).foreach { case (modelValue, jsonValue) =>
      s"must serialise and deserialise $jsonValue" in {
        Json.toJson(modelValue) mustBe JsString(jsonValue)
        JsString(jsonValue).as[FileUploadValidationStatus] mustBe modelValue
      }
    }

    "must fail to deserialise an unknown status" in {
      JsString("Unknown").validate[FileUploadValidationStatus] mustBe
        JsError("Invalid file upload validation status: Unknown")
    }

    "must fail to deserialise a non-string value" in {
      Json.obj("status" -> "ValidationSuccess").validate[FileUploadValidationStatus] mustBe
        JsError("File upload validation status must be a string")
    }
  }

  "FileUploadValidationResult format" -
    Seq(
      FileUploadValidationStatus.ValidationSuccess,
      FileUploadValidationStatus.ValidationFailed,
      FileUploadValidationStatus.InvalidFile
    ).foreach { status =>
      s"must serialise and deserialise $status" in {
        val result = FileUploadValidationResult(10, 2, status)

        Json.toJson(result).as[FileUploadValidationResult] mustBe result
      }
    }

  "FileUploadValidationError format" - {

    "must round-trip to JSON" in {
      val error = FileUploadValidationError(
        rowNumber = 1,
        errorCodes = List(nationalInsuranceRequiredErrorCode, invalidFirstNameErrorCode)
      )

      Json.toJson(error).as[FileUploadValidationError] mustBe error
    }
  }

  "FileUploadValidationResult format with inlineErrors" - {

    "must round-trip to JSON with inline errors" in {
      val result = FileUploadValidationResult(
        rowsValidated = 10,
        validationErrors = 3,
        status = FileUploadValidationStatus.ValidationFailed,
        inlineErrors = List(
          FileUploadValidationError(
            rowNumber = 1,
            errorCodes = List(nationalInsuranceRequiredErrorCode, invalidFirstNameErrorCode)
          ),
          FileUploadValidationError(rowNumber = 2, errorCodes = List(invalidCurrentYearSubscriptionsErrorCode))
        )
      )

      Json.toJson(result).as[FileUploadValidationResult] mustBe result
    }

    "must read old JSON without inlineErrors as empty inlineErrors" in {
      val json = Json.parse("""
        {
          "rowsValidated": 100,
          "validationErrors": 3,
          "status": "ValidationFailed"
        }
        """)

      json.as[FileUploadValidationResult] mustBe FileUploadValidationResult(
        rowsValidated = 100,
        validationErrors = 3,
        status = FileUploadValidationStatus.ValidationFailed,
        inlineErrors = Nil
      )
    }

    "must support ValidationSuccess with empty inline errors" in {
      val result = FileUploadValidationResult(10, 0, FileUploadValidationStatus.ValidationSuccess, Nil)

      Json.toJson(result).as[FileUploadValidationResult] mustBe result
    }

    "must support InvalidFile with empty inline errors" in {
      val result = FileUploadValidationResult(0, 0, FileUploadValidationStatus.InvalidFile, Nil)

      Json.toJson(result).as[FileUploadValidationResult] mustBe result
    }
  }
}
