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

import com.github.tomakehurst.wiremock.client.WireMock.{get as wmGet, *}
import com.github.tomakehurst.wiremock.http.Fault
import play.api.http.Status.*
import play.api.libs.json.Json
import play.api.libs.ws.WSResponse
import uk.gov.hmrc.disareturnsbackend.BaseIntegrationSpec
import uk.gov.hmrc.disareturnsbackend.repositories.MonthlyReturnRepository

abstract class AgentAuthenticationISpec extends BaseIntegrationSpec {
  protected def agentAuthenticationEnabled: Boolean

  override def config: Map[String, Any] = super.config ++ Map(
    "features.agent-authentication-enabled" -> agentAuthenticationEnabled
  )

  private val preferencesPath = "/disa-account/agents/preferences"
  private val reportingPath   = s"$testServicePath/reporting-window/status/$testZReference"
  private val monthlyPath     = s"$testServicePath/monthly/$testZReference/$testTaxYear/$testMonth"
  private val filesPath       = s"$monthlyPath/files"
  private val filePath        = s"$filesPath/$testUploadReference"
  private val fileBody        = Json.obj("reference" -> testUploadReference)
  private lazy val repository = inject[MonthlyReturnRepository]

  private case class Endpoint(name: String, request: () => WSResponse)
  private lazy val endpoints = Seq(
    Endpoint("reporting status", () => get(reportingPath)),
    Endpoint("get monthly return", () => get(monthlyPath)),
    Endpoint("create monthly return", () => postJson(monthlyPath, nilReturnFalseRequest)),
    Endpoint("declare monthly return", () => postJson(s"$monthlyPath/declarations", Json.obj())),
    Endpoint("create file", () => postJson(filesPath, fileBody)),
    Endpoint("get file", () => get(filePath)),
    Endpoint("delete file", () => delete(filePath)),
    Endpoint("update nil return", () => putJson(s"$monthlyPath/nilReturn", Json.obj("value" -> true)))
  )

  private def seedMonthlyReturn(): Unit = {
    postJson(monthlyPath, nilReturnFalseRequest).status shouldBe CREATED
    postJson(filesPath, fileBody).status                shouldBe CREATED
    resetAllRequests()
  }

  private def assertNoProtectedOperation(): Unit = {
    verify(0, anyRequestedFor(urlPathMatching("/disa-returns-submission/.*")))
    verify(1, postRequestedFor(urlEqualTo("/auth/authorise")))
  }

  s"Agent authentication enabled=$agentAuthenticationEnabled" should {
    "preserve ISA manager access without retrieving preferences" in {
      get(reportingPath).status                           shouldBe OK
      postJson(monthlyPath, nilReturnFalseRequest).status shouldBe CREATED
      get(monthlyPath).status                             shouldBe OK
      verify(0, getRequestedFor(urlEqualTo(preferencesPath)))
    }

    if (!agentAuthenticationEnabled) {
      endpoints.foreach { endpoint =>
        Seq(false, true).foreach { matchingPrincipal =>
          s"reject ${endpoint.name} for disabled agents with matching principal enrolment=$matchingPrincipal" in {
            seedMonthlyReturn()
            val original = repository.get(testZReference, testTaxYear, testMonth).futureValue
            stubAuth(affinityGroup = "Agent", includeDisaEnrolment = matchingPrincipal)

            endpoint.request().status shouldBe FORBIDDEN

            verify(0, getRequestedFor(urlEqualTo(preferencesPath)))
            assertNoProtectedOperation()
            repository.get(testZReference, testTaxYear, testMonth).futureValue shouldBe original
          }
        }
      }
    } else {
      "forward the bearer token and allow reporting status and the monthly return lifecycle without a DISA principal enrolment" in {
        stubAuth(affinityGroup = "Agent", includeDisaEnrolment = false)
        stubFor(
          wmGet(urlEqualTo(preferencesPath)).willReturn(
            okJson(Json.obj("zReference" -> lowercaseTestZReference).toString())
          )
        )

        get(s"$testServicePath/reporting-window/status/$lowercaseTestZReference").status shouldBe OK
        postJson(monthlyPath, nilReturnFalseRequest).status                              shouldBe CREATED
        get(monthlyPath).status                                                          shouldBe OK
        postJson(filesPath, fileBody).status                                             shouldBe CREATED
        get(filePath).status                                                             shouldBe OK
        putJson(s"$monthlyPath/nilReturn", Json.obj("value" -> false)).status            shouldBe OK
        delete(filePath).status                                                          shouldBe NO_CONTENT
        putJson(s"$monthlyPath/nilReturn", Json.obj("value" -> true)).status             shouldBe OK
        postJson(s"$monthlyPath/declarations", Json.obj()).status                        shouldBe NO_CONTENT

        verify(9, getRequestedFor(urlEqualTo(preferencesPath)).withHeader("Authorization", equalTo(testBearerToken)))
        verify(9, postRequestedFor(urlEqualTo("/auth/authorise")))
      }

      val failures = Seq(
        ("different selection", OK, Json.obj("zReference" -> wrongTestZReference).toString(), FORBIDDEN),
        ("expired authentication", UNAUTHORIZED, "", UNAUTHORIZED),
        ("delegated access denied", FORBIDDEN, "", FORBIDDEN),
        ("missing or expired preferences", NOT_FOUND, "", FORBIDDEN),
        ("unavailable preferences", SERVICE_UNAVAILABLE, "", SERVICE_UNAVAILABLE),
        ("unexpected upstream failure", INTERNAL_SERVER_ERROR, "", SERVICE_UNAVAILABLE),
        ("unexpected upstream client error", BAD_REQUEST, "", SERVICE_UNAVAILABLE),
        ("malformed JSON", OK, "not-json", SERVICE_UNAVAILABLE),
        ("missing Z-reference", OK, "{}", SERVICE_UNAVAILABLE),
        ("invalid Z-reference", OK, Json.obj("zReference" -> invalidTestZReference).toString(), SERVICE_UNAVAILABLE)
      )

      endpoints.foreach { endpoint =>
        failures.foreach { case (description, upstreamStatus, body, expectedStatus) =>
          s"reject ${endpoint.name} for $description without executing the protected operation" in {
            seedMonthlyReturn()
            val original = repository.get(testZReference, testTaxYear, testMonth).futureValue
            stubAuth(affinityGroup = "Agent")
            stubFor(
              wmGet(urlEqualTo(preferencesPath)).willReturn(aResponse().withStatus(upstreamStatus).withBody(body))
            )

            endpoint.request().status shouldBe expectedStatus

            verify(
              1,
              getRequestedFor(urlEqualTo(preferencesPath)).withHeader("Authorization", equalTo(testBearerToken))
            )
            assertNoProtectedOperation()
            repository.get(testZReference, testTaxYear, testMonth).futureValue shouldBe original
          }
        }
      }

      Seq(reportingPath, monthlyPath).foreach { path =>
        s"return 503 for a preferences connection failure at $path" in {
          stubAuth(affinityGroup = "Agent", includeDisaEnrolment = false)
          stubFor(wmGet(urlEqualTo(preferencesPath)).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)))

          get(path).status shouldBe SERVICE_UNAVAILABLE

          assertNoProtectedOperation()
        }
      }
    }
  }
}

class AgentAuthenticationEnabledISpec extends AgentAuthenticationISpec {
  override protected def agentAuthenticationEnabled: Boolean = true
}

class AgentAuthenticationDisabledISpec extends AgentAuthenticationISpec {
  override protected def agentAuthenticationEnabled: Boolean = false
}
