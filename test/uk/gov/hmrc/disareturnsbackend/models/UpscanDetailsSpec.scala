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

import play.api.libs.json.{JsValue, Json, OFormat}
import uk.gov.hmrc.disareturnsbackend.utils.JsonFormatSpec

import java.time.Instant

class UpscanDetailsSpec extends JsonFormatSpec[UpscanDetails] {

  override def model: UpscanDetails =
    UpscanDetails(
      fileName = "return.csv",
      fileMimeType = "text/csv",
      uploadTimestamp = Instant.parse("2026-05-17T12:00:00Z"),
      checksum = "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
      size = 1024L
    )

  override def expectedJsonFromWrites: JsValue =
    Json.parse(
      """
        |{
        |  "fileName": "return.csv",
        |  "fileMimeType": "text/csv",
        |  "uploadTimestamp": "2026-05-17T12:00:00Z",
        |  "checksum": "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
        |  "size": 1024
        |}
        |""".stripMargin
    )

  override implicit def format: OFormat[UpscanDetails] = UpscanDetails.format
}
