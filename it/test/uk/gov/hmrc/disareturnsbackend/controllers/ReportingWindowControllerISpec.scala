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

package uk.gov.hmrc.disareturnsbackend.controllers

import com.github.tomakehurst.wiremock.client.WireMock.*
import play.api.http.Status.*
import play.api.libs.json.Json
import uk.gov.hmrc.disareturnsbackend.BaseIntegrationSpec

class ReportingWindowControllerISpec extends BaseIntegrationSpec {
  private val path = s"$testServicePath/reporting-window/status/$testZReference"

  "GET reporting-window/status/:zReference" should {
    "return submission's authoritative status for the normalized Z-reference" in {
      stubReturnsSubmissionReportingWindow(open = false)

      val result = get(s"$testServicePath/reporting-window/status/$lowercaseTestZReference")

      result.status shouldBe OK
      result.json shouldBe Json.obj("reportingWindowOpen" -> false)
      verify(getRequestedFor(urlEqualTo(s"/disa-returns-submission/reporting-window/status/$testZReference")))
    }

    "return BadRequest for an invalid Z-reference" in {
      get(s"$testServicePath/reporting-window/status/$invalidTestZReference").status shouldBe BAD_REQUEST
    }

    "return Unauthorized without a bearer token" in {
      getWithoutAuthorization(path).status shouldBe UNAUTHORIZED
    }

    "return Unauthorized with an invalid bearer token" in {
      stubInvalidBearerToken()

      getWithAuthorization(path, invalidTestBearerToken).status shouldBe UNAUTHORIZED
    }

    "return Forbidden without a matching enrolment" in {
      stubAuth(wrongTestZReference)

      get(path).status shouldBe FORBIDDEN
    }

    "return Forbidden when the matching enrolment is not activated" in {
      stubAuth(testZReference, state = "NotYetActivated")

      get(path).status shouldBe FORBIDDEN
    }

    "return ServiceUnavailable when submission fails" in {
      stubReturnsSubmissionReportingWindow(status = BAD_REQUEST)

      get(path).status shouldBe SERVICE_UNAVAILABLE
    }
  }
}
