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

package uk.gov.hmrc.disareturnsbackend.services

import com.github.tomakehurst.wiremock.client.WireMock.{get as wireMockGet, *}
import com.github.tomakehurst.wiremock.stubbing.Scenario
import play.api.http.Status.*
import play.api.libs.json.Json
import uk.gov.hmrc.disareturnsbackend.WireMockIntegrationSpec
import uk.gov.hmrc.disareturnsbackend.models.*
import uk.gov.hmrc.disareturnsbackend.repositories.MonthlyReturnRepository
import uk.gov.hmrc.disareturnsbackend.utils.Constants.CSV_MIME_TYPE
import uk.gov.hmrc.disareturnsbackend.utils.{ObjectStoreWireMockStubs, UpscanWireMockStubs}

import java.security.MessageDigest
import java.util.Base64

class MonthlyReturnSubmissionProcessingISpec
    extends WireMockIntegrationSpec
    with ObjectStoreWireMockStubs
    with UpscanWireMockStubs {

  private val monthlyPath    = s"$testServicePath/monthly/$testZReference/$testTaxYear/$testMonth"
  private val filesPath      = s"$monthlyPath/files"
  private val submissionPath =
    s"/disa-returns-submission/monthly/$testZReference/$testTaxYear/$testMonth/submissions/$testUploadReference"
  private val objectStorePath = s"/object-store/object/disa-returns-backend/$testUploadReference"
  private val csvContent      = readResourceBytes("file-upload/monthly/isa-open-valid.csv")
  private val fileDetails     = FileUploadDetails(
    fileName = testFileName,
    fileMimeType = CSV_MIME_TYPE,
    uploadTimestamp = testCreatedOn,
    checksum = testChecksum,
    size = testFileSize,
    upscanDownloadUrl = testDownloadUrl
  )

  private val monthlyReturnRepository = inject[MonthlyReturnRepository]
  private val processingService       = inject[MonthlyReturnSubmissionProcessingService]

  "monthly return submission processing" should {
    "retry a transient Object Store download failure" in {
      postJson(monthlyPath, nilReturnFalseRequest).status shouldBe CREATED
      postJson(filesPath, Json.obj(referenceFieldName -> testUploadReference)).status shouldBe CREATED

      monthlyReturnRepository
        .completeUpscan(
          testZReference,
          testTaxYear,
          testMonth,
          testUploadReference,
          FileUploadStatus.UpscanSuccess,
          Some(fileDetails)
        )
        .futureValue shouldBe true
      monthlyReturnRepository
        .updateFileUploadProcessingDetails(
          testZReference,
          testTaxYear,
          testMonth,
          testUploadReference,
          FileUploadValidationResult(1, 0, FileUploadValidationStatus.ValidationSuccess),
          Some("object-store-location"),
          None
        )
        .futureValue shouldBe true

      stubObjectStoreGetWithRetry()
      stubFor(put(urlEqualTo(submissionPath)).willReturn(aResponse().withStatus(OK)))

      processingService
        .process(MonthlyReturnSubmissionWorkItem(testZReference, testTaxYear, testMonth, testUploadReference))
        .futureValue

      verify(2, getRequestedFor(urlEqualTo(objectStorePath)))
      verify(1, putRequestedFor(urlEqualTo(submissionPath)))
    }
  }

  private def stubObjectStoreGetWithRetry(): Unit = {
    val scenario = "object-store submission download retry"

    stubFor(
      wireMockGet(urlEqualTo(objectStorePath))
        .inScenario(scenario)
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR))
        .willSetStateTo("recovered")
    )
    stubFor(
      wireMockGet(urlEqualTo(objectStorePath))
        .inScenario(scenario)
        .whenScenarioStateIs("recovered")
        .willReturn(
          aResponse()
            .withStatus(OK)
            .withHeader("Content-Type", CSV_MIME_TYPE)
            .withHeader("Content-Length", csvContent.length.toString)
            .withHeader("Content-MD5", md5Base64(csvContent))
            .withHeader("Last-Modified", "Sun, 17 May 2026 12:00:00 GMT")
            .withBody(csvContent)
        )
    )
  }

  private def md5Base64(value: Array[Byte]): String =
    Base64.getEncoder.encodeToString(MessageDigest.getInstance("MD5").digest(value))
}
