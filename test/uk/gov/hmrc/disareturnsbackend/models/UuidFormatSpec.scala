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
import play.api.libs.json.{JsError, JsNumber, JsString, Json}

class UuidFormatSpec extends SpecBase {

  "UuidFormat" - {

    "must write UUIDs as strings" in {
      Json.toJson(testSubmissionId)(UuidFormat.format) mustBe JsString(testSubmissionId.toString)
    }

    "must read valid UUID strings" in {
      JsString(testSubmissionId.toString).validate(UuidFormat.format) mustBe play.api.libs.json
        .JsSuccess(testSubmissionId)
    }

    "must fail to read invalid UUID strings" in {
      JsString("not-a-uuid").validate(UuidFormat.format) mustBe JsError("error.expected.uuid")
    }

    "must fail to read non-string values" in {
      JsNumber(1).validate(UuidFormat.format) mustBe JsError("error.expected.uuid")
    }
  }
}
