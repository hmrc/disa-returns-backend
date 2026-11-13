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

package uk.gov.hmrc.disareturnsbackend.utils

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.stubbing.Scenario
import com.github.tomakehurst.wiremock.verification.LoggedRequest
import org.scalatest.Assertions.fail
import play.api.http.Status.{INTERNAL_SERVER_ERROR, OK}
import play.api.libs.json.Json

import scala.jdk.CollectionConverters.*

trait ObjectStoreWireMockStubs {

  private val objectStoreOwner = "disa-returns-backend"
  private val recoveredState   = "recovered"

  protected def stubObjectStorePut(objectName: String): Unit =
    stubFor(
      put(urlEqualTo(objectStorePath(objectName)))
        .willReturn(successfulPutResponse(objectName))
    )

  protected def stubObjectStorePutWithRetry(objectName: String): Unit = {
    val path     = objectStorePath(objectName)
    val scenario = s"object-store put retry $objectName"

    stubFor(
      put(urlEqualTo(path))
        .inScenario(scenario)
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR))
        .willSetStateTo(recoveredState)
    )
    stubFor(
      put(urlEqualTo(path))
        .inScenario(scenario)
        .whenScenarioStateIs(recoveredState)
        .willReturn(successfulPutResponse(objectName))
    )
  }

  protected def verifyObjectStorePut(objectName: String): Unit =
    verify(putRequestedFor(urlEqualTo(objectStorePath(objectName))))

  protected def verifyObjectStorePutNotMade(objectName: String): Unit =
    verify(0, putRequestedFor(urlEqualTo(objectStorePath(objectName))))

  protected def objectStorePutRequestBody(objectName: String): Array[Byte] =
    findAll(putRequestedFor(urlEqualTo(objectStorePath(objectName)))).asScala.headOption
      .map(_.getBody)
      .getOrElse(fail(s"No object-store PUT request found for [$objectName]"))

  protected def objectStorePutRequests(objectName: String): Seq[LoggedRequest] =
    findAll(putRequestedFor(urlEqualTo(objectStorePath(objectName)))).asScala.toSeq

  private def successfulPutResponse(objectName: String) =
    aResponse()
      .withStatus(OK)
      .withHeader("Content-Type", "application/json")
      .withBody(
        Json
          .obj(
            "location"      -> objectName,
            "contentLength" -> 123,
            "contentMD5"    -> "CY9rzUYh03PK3k6DJie09g==",
            "lastModified"  -> "2026-05-17T12:00:00Z"
          )
          .toString()
      )

  private def objectStorePath(objectName: String): String =
    s"/object-store/object/$objectStoreOwner/$objectName"
}
