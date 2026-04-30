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

import play.api.libs.json.{Format, JsError, JsResult, JsString, JsSuccess, JsValue}

enum FileUploadStatus:
  case UpscanSucceeded, ReadyForParsing, ParseSucceeded, ParseFailed

object FileUploadStatus {
  implicit val format: Format[FileUploadStatus] = new Format[FileUploadStatus] {
    override def reads(json: JsValue): JsResult[FileUploadStatus] =
      json match {
        case JsString("UpscanSucceeded") => JsSuccess(FileUploadStatus.UpscanSucceeded)
        case JsString("ReadyForParsing") => JsSuccess(FileUploadStatus.ReadyForParsing)
        case JsString("ParseSucceeded")  => JsSuccess(FileUploadStatus.ParseSucceeded)
        case JsString("ParseFailed")     => JsSuccess(FileUploadStatus.ParseFailed)
        case JsString(value)             => JsError(s"Unknown FileUploadStatus: $value")
        case _                           => JsError("FileUploadStatus must be a string")
      }

    override def writes(o: FileUploadStatus): JsValue =
      JsString(o.toString)
  }
}
