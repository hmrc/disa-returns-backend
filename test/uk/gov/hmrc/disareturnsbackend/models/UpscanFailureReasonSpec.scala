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
import uk.gov.hmrc.disareturnsbackend.models.UpscanFailureReason._

class UpscanFailureReasonSpec extends SpecBase {

  "UpscanFailureReason format" - {

    "must serialise Quarantine" in {
      Json.toJson[UpscanFailureReason](Quarantine) mustBe JsString(quarantineReasonString)
    }

    "must serialise Rejected" in {
      Json.toJson[UpscanFailureReason](Rejected) mustBe JsString(rejectedReasonString)
    }

    "must serialise Unknown" in {
      Json.toJson[UpscanFailureReason](Unknown) mustBe JsString(unknownReasonString)
    }

    "must deserialise Quarantine" in {
      JsString(quarantineReasonString).as[UpscanFailureReason] mustBe Quarantine
    }

    "must deserialise Rejected" in {
      JsString(rejectedReasonString).as[UpscanFailureReason] mustBe Rejected
    }

    "must deserialise Unknown" in {
      JsString(unknownReasonString).as[UpscanFailureReason] mustBe Unknown
    }

    "must fail to deserialise an unknown reason" in {
      JsString(invalidFailureReasonString).validate[UpscanFailureReason] mustBe
        JsError(s"Invalid upscan failure reason: $invalidFailureReasonString")
    }

    "must fail to deserialise a non-string value" in {
      Json.obj("x" -> 1).validate[UpscanFailureReason] mustBe
        JsError("Upscan failure reason must be a string")
    }
  }
}
