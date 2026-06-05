/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.disareturnsbackend

import play.api.http.Status
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.HttpReads.Implicits.readRaw
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}

class HealthEndpointIntegrationSpec extends BaseIntegrationSpec:

  private val httpClient = app.injector.instanceOf[HttpClientV2]

  "service health endpoint" should:
    "respond with 200 status" in:
      val response =
        httpClient
          .get(url"${serviceUrl("/ping/ping")}")(HeaderCarrier())
          .execute()
          .futureValue

      response.status shouldBe Status.OK
