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

package uk.gov.hmrc.disareturnsbackend.models.upscan

import base.SpecBase
import play.api.libs.json.{JsError, JsString, Json}
import uk.gov.hmrc.disareturnsbackend.models.upscan.UpscanUploadFailureReason._

class UpscanUploadFailureReasonSpec extends SpecBase {

  "UpscanUploadFailureReason format" - {

    "must serialise Quarantine" in {
      Json.toJson[UpscanUploadFailureReason](Quarantine) mustBe JsString("QUARANTINE")
    }

    "must serialise Rejected" in {
      Json.toJson[UpscanUploadFailureReason](Rejected) mustBe JsString("REJECTED")
    }

    "must serialise Unknown" in {
      Json.toJson[UpscanUploadFailureReason](Unknown) mustBe JsString("UNKNOWN")
    }

    "must deserialise Quarantine" in {
      JsString("QUARANTINE").as[UpscanUploadFailureReason] mustBe Quarantine
    }

    "must deserialise Rejected" in {
      JsString("REJECTED").as[UpscanUploadFailureReason] mustBe Rejected
    }

    "must deserialise Unknown" in {
      JsString("UNKNOWN").as[UpscanUploadFailureReason] mustBe Unknown
    }

    "must fail to deserialise an unknown reason" in {
      JsString("DUPLICATE").validate[UpscanUploadFailureReason] mustBe
        JsError("Invalid upscan upload failure reason: DUPLICATE")
    }

    "must fail to deserialise a non-string value" in {
      Json.obj("x" -> 1).validate[UpscanUploadFailureReason] mustBe
        JsError("Upscan upload failure reason must be a string")
    }
  }
}
