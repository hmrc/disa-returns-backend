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

package uk.gov.hmrc.disareturnsbackend.connectors

import base.SpecBase
import com.github.tomakehurst.wiremock.client.WireMock.*
import org.scalatest.BeforeAndAfterEach
import play.api.Application
import play.api.http.Status.*
import play.api.libs.json.Json
import uk.gov.hmrc.disareturnsbackend.connectors.ReturnsSubmissionConnector.{CreateMonthlyReturnSubmissionResult, DeclareMonthlyReturnSubmissionResult}
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.http.test.WireMockSupport

class ReturnsSubmissionConnectorSpec extends SpecBase with WireMockSupport with BeforeAndAfterEach {

  override lazy val app: Application = applicationBuilder()
    .configure(
      "microservice.services.disa-returns-submission.protocol" -> "http",
      "microservice.services.disa-returns-submission.host"     -> "localhost",
      "microservice.services.disa-returns-submission.port"     -> wireMockPort
    )
    .build()

  private val connector = inject[ReturnsSubmissionConnector]

  private implicit val hc: HeaderCarrier = HeaderCarrier()

  private val path            = s"/disa-returns-submission/monthly/$testZReference/$testTaxYear/$testMonth"
  private val declarationPath = s"$path/declarations"

  "ReturnsSubmissionConnector.createMonthlyReturn" - {

    "must return Created when submission creates the monthly return" in {
      stubCreateMonthlyReturnResponse(CREATED)

      connector
        .createMonthlyReturn(testZReference, testTaxYear, testMonth, nilReturn = false)
        .futureValue mustBe CreateMonthlyReturnSubmissionResult.Created(testSubmissionId)

      verifySubmissionRequest(nilReturn = false)
    }

    "must return AlreadyExists when submission already has the monthly return" in {
      stubCreateMonthlyReturnResponse(CONFLICT)

      connector
        .createMonthlyReturn(testZReference, testTaxYear, testMonth, nilReturn = true)
        .futureValue mustBe CreateMonthlyReturnSubmissionResult.AlreadyExists(testSubmissionId)

      verifySubmissionRequest(nilReturn = true)
    }

    "must return OutsideDeclarationPeriod when submission rejects the create" in {
      stubFor(post(urlEqualTo(path)).willReturn(aResponse().withStatus(UNPROCESSABLE_ENTITY)))

      connector
        .createMonthlyReturn(testZReference, testTaxYear, testMonth, nilReturn = false)
        .futureValue mustBe CreateMonthlyReturnSubmissionResult.OutsideDeclarationPeriod
    }

    "must fail when submission returns an unexpected response" in {
      stubFor(post(urlEqualTo(path)).willReturn(aResponse().withStatus(BAD_REQUEST)))

      connector
        .createMonthlyReturn(testZReference, testTaxYear, testMonth, nilReturn = false)
        .failed
        .futureValue mustBe a[UpstreamErrorResponse]
    }
  }

  "ReturnsSubmissionConnector.getMonthlyReturn" - {

    "must return the monthly return JSON when submission returns OK" in {
      val responseJson = Json.obj(
        zReferenceFieldName   -> testZReference,
        submissionIdFieldName -> testSubmissionId,
        taxYearFieldName      -> testTaxYear,
        monthFieldName        -> testMonth,
        declaredOnFieldName   -> testCreatedOnString
      )
      stubFor(
        get(urlEqualTo(path))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withHeader("Content-Type", "application/json")
              .withBody(responseJson.toString())
          )
      )

      connector.getMonthlyReturn(testZReference, testTaxYear, testMonth).futureValue mustBe Some(responseJson)
    }

    "must return None when submission returns NOT_FOUND" in {
      stubFor(get(urlEqualTo(path)).willReturn(aResponse().withStatus(NOT_FOUND)))

      connector.getMonthlyReturn(testZReference, testTaxYear, testMonth).futureValue mustBe None
    }

    "must fail when submission returns an unexpected response" in {
      stubFor(get(urlEqualTo(path)).willReturn(aResponse().withStatus(BAD_REQUEST)))

      connector
        .getMonthlyReturn(testZReference, testTaxYear, testMonth)
        .failed
        .futureValue mustBe a[UpstreamErrorResponse]
    }
  }

  "ReturnsSubmissionConnector.declareMonthlyReturn" - {

    val testNilReturn = false

    "must return Declared when submission declares the monthly return" in {
      stubFor(post(urlEqualTo(declarationPath)).willReturn(aResponse().withStatus(OK)))

      connector.declareMonthlyReturn(testZReference, testTaxYear, testMonth, testNilReturn).futureValue mustBe
        DeclareMonthlyReturnSubmissionResult.Declared
    }

    "must return MonthlyReturnNotFound when submission cannot find the monthly return" in {
      stubFor(post(urlEqualTo(declarationPath)).willReturn(aResponse().withStatus(NOT_FOUND)))

      connector.declareMonthlyReturn(testZReference, testTaxYear, testMonth, testNilReturn).futureValue mustBe
        DeclareMonthlyReturnSubmissionResult.MonthlyReturnNotFound
    }

    "must return AlreadyDeclared when submission rejects the declaration" in {
      stubFor(post(urlEqualTo(declarationPath)).willReturn(aResponse().withStatus(UNPROCESSABLE_ENTITY)))

      connector.declareMonthlyReturn(testZReference, testTaxYear, testMonth, testNilReturn).futureValue mustBe
        DeclareMonthlyReturnSubmissionResult.AlreadyDeclared
    }

    "must fail when submission returns an unexpected response" in {
      stubFor(post(urlEqualTo(declarationPath)).willReturn(aResponse().withStatus(BAD_REQUEST)))

      connector
        .declareMonthlyReturn(testZReference, testTaxYear, testMonth, testNilReturn)
        .failed
        .futureValue mustBe a[UpstreamErrorResponse]
    }
  }

  private def stubCreateMonthlyReturnResponse(status: Int): Unit =
    stubFor(
      post(urlEqualTo(path))
        .willReturn(
          aResponse()
            .withStatus(status)
            .withHeader("Content-Type", "application/json")
            .withBody(Json.obj("submissionId" -> testSubmissionId).toString())
        )
    )

  private def verifySubmissionRequest(nilReturn: Boolean): Unit =
    verify(
      postRequestedFor(urlEqualTo(path))
        .withRequestBody(equalToJson(Json.obj("nilReturn" -> nilReturn).toString()))
    )
}
