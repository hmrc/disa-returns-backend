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
import play.api.libs.json.{JsObject, JsString, Json}

class FileUploadDetailsSpec extends SpecBase {

  private val details = FileUploadDetails(
    fileName = testFileName,
    fileMimeType = testFileMimeType,
    uploadTimestamp = testCreatedOn,
    checksum = testChecksum,
    size = testFileSize,
    upscanDownloadUrl = testDownloadUrl
  )

  "FileUploadDetails format" - {

    "must default object-store locations to None" in {
      details.objectStoreFileLocation mustBe None
      details.objectStoreFileErrorsLocation mustBe None
    }

    "must serialise and deserialise object-store locations when present" in {
      val populated = details.copy(
        objectStoreFileLocation = Some("original-location"),
        objectStoreFileErrorsLocation = Some("errors-location")
      )

      Json.toJson(populated).as[FileUploadDetails] mustBe populated
    }

    "must read missing object-store locations as None" in {
      val json = Json.toJson(details).as[JsObject] - "objectStoreFileLocation" - "objectStoreFileErrorsLocation"

      json.as[FileUploadDetails].objectStoreFileLocation mustBe None
      json.as[FileUploadDetails].objectStoreFileErrorsLocation mustBe None
    }

    "must read legacy empty-string object-store locations as None" in {
      val json = Json.toJson(details).as[JsObject] ++ Json.obj(
        "objectStoreFileLocation"       -> JsString(""),
        "objectStoreFileErrorsLocation" -> JsString(" ")
      )

      json.as[FileUploadDetails].objectStoreFileLocation mustBe None
      json.as[FileUploadDetails].objectStoreFileErrorsLocation mustBe None
    }

    "must preserve non-empty object-store location values" in {
      val json = Json.toJson(details).as[JsObject] ++ Json.obj(
        "objectStoreFileLocation"       -> JsString("original-location"),
        "objectStoreFileErrorsLocation" -> JsString("errors-location")
      )

      json.as[FileUploadDetails].objectStoreFileLocation mustBe Some("original-location")
      json.as[FileUploadDetails].objectStoreFileErrorsLocation mustBe Some("errors-location")
    }

    "must fail to read non-object JSON" in {
      JsString("not-an-object").validate[FileUploadDetails].isError mustBe true
    }

    "must keep validation optional and backwards compatible" in {
      val json = Json.toJson(details).as[JsObject] - "validation"

      json.as[FileUploadDetails].validation mustBe None
    }

    "must preserve inline validation errors" in {
      val validation = FileUploadValidationResult(
        rowsValidated = 10,
        validationErrors = 2,
        status = FileUploadValidationStatus.ValidationFailed,
        inlineErrors = List(
          FileUploadValidationError(
            rowNumber = 1,
            errorCodes = List(accountNumberRequiredErrorCode, invalidNationalInsuranceErrorCode)
          )
        )
      )
      val populated  = details.copy(validation = Some(validation))

      Json.toJson(populated).as[FileUploadDetails] mustBe populated
    }
  }
}
