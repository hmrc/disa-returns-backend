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
import com.github.tomakehurst.wiremock.stubbing.Scenario
import com.typesafe.config.{Config, ConfigFactory}
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.util.ByteString
import play.api.Application
import play.api.http.Status.*
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.test.WireMockSupport
import uk.gov.hmrc.objectstore.client.Path
import uk.gov.hmrc.objectstore.client.play.PlayObjectStoreClient

import java.nio.file.Files
import java.security.MessageDigest
import java.util.Base64

class RetryingConnectorWireMockSpec extends SpecBase with WireMockSupport {

  private val objectStoreOwner       = "disa-returns-backend"
  private val objectName             = "retry-test-file"
  private val objectStorePath        = s"/object-store/object/$objectStoreOwner/$objectName"
  private val fileContent            = "file-content"
  private val recoveredState         = "recovered"
  private val objectStorePutScenario = "object-store put retry"
  private val objectStoreGetScenario = "object-store get retry"
  private val upscanDownloadScenario = "upscan download retry"
  private val upscanTimeoutScenario  = "upscan timeout retry"
  private val retryConfig: Config    = ConfigFactory.parseString(
    "http-verbs.retries.intervals = [1 millisecond, 1 millisecond, 1 millisecond]"
  )

  override lazy val app: Application = applicationBuilder()
    .configure(
      "microservice.services.object-store.protocol" -> "http",
      "microservice.services.object-store.host"     -> "localhost",
      "microservice.services.object-store.port"     -> wireMockPort,
      "play.ws.timeout.request"                     -> "250ms"
    )
    .build()

  private implicit val hc: HeaderCarrier          = HeaderCarrier()
  private implicit val materializer: Materializer = inject[Materializer]

  "ObjectStoreConnector.putFile" - {
    "must retry a 5xx response and succeed on the next request" in {
      val file = Files.createTempFile("object-store-retry", ".txt")
      Files.writeString(file, fileContent)

      stubFor(
        put(urlEqualTo(objectStorePath))
          .inScenario(objectStorePutScenario)
          .whenScenarioStateIs(Scenario.STARTED)
          .willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR))
          .willSetStateTo(recoveredState)
      )
      stubFor(
        put(urlEqualTo(objectStorePath))
          .inScenario(objectStorePutScenario)
          .whenScenarioStateIs(recoveredState)
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withHeader("Content-Type", "application/json")
              .withBody(
                s"""{"location":"$objectName","contentLength":${fileContent.length},"contentMD5":"${md5Base64(
                    fileContent
                  )}","lastModified":"2026-05-17T12:00:00Z"}"""
              )
          )
      )

      try {
        val connector = new ObjectStoreConnector(inject[PlayObjectStoreClient], inject[ActorSystem], retryConfig)

        connector.putFile(objectName, file, "text/plain").futureValue mustBe Path.Directory("").file(objectName).asUri
        verify(2, putRequestedFor(urlEqualTo(objectStorePath)))
      } finally Files.deleteIfExists(file)
    }
  }

  "ObjectStoreConnector.getFile" - {
    "must retry a 5xx response and succeed on the next request" in {
      stubFor(
        get(urlEqualTo(objectStorePath))
          .inScenario(objectStoreGetScenario)
          .whenScenarioStateIs(Scenario.STARTED)
          .willReturn(aResponse().withStatus(SERVICE_UNAVAILABLE))
          .willSetStateTo(recoveredState)
      )
      stubFor(
        get(urlEqualTo(objectStorePath))
          .inScenario(objectStoreGetScenario)
          .whenScenarioStateIs(recoveredState)
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withHeader("Content-Type", "text/plain")
              .withHeader("Content-Length", fileContent.length.toString)
              .withHeader("Content-MD5", md5Base64(fileContent))
              .withHeader("Last-Modified", "Sun, 17 May 2026 12:00:00 GMT")
              .withBody(fileContent)
          )
      )

      val connector = new ObjectStoreConnector(inject[PlayObjectStoreClient], inject[ActorSystem], retryConfig)
      val result    = connector
        .getFile(objectName)
        .futureValue
        .runWith(Sink.fold(ByteString.empty)(_ ++ _))
        .futureValue

      result mustBe ByteString(fileContent)
      verify(2, getRequestedFor(urlEqualTo(objectStorePath)))
    }
  }

  "UpscanConnector.downloadFile" - {
    "must retry a 5xx response and succeed on the next request" in {
      val path = "/upscan/retry-test-file"

      stubFor(
        get(urlEqualTo(path))
          .inScenario(upscanDownloadScenario)
          .whenScenarioStateIs(Scenario.STARTED)
          .willReturn(aResponse().withStatus(SERVICE_UNAVAILABLE).withBody("temporarily unavailable"))
          .willSetStateTo(recoveredState)
      )
      stubFor(
        get(urlEqualTo(path))
          .inScenario(upscanDownloadScenario)
          .whenScenarioStateIs(recoveredState)
          .willReturn(aResponse().withStatus(OK).withBody(fileContent))
      )

      val connector = new UpscanConnector(inject[HttpClientV2], retryConfig, inject[ActorSystem])
      val result    = connector
        .downloadFile(s"$wireMockUrl$path")
        .futureValue
        .runWith(Sink.fold(ByteString.empty)(_ ++ _))
        .futureValue

      result mustBe ByteString(fileContent)
      verify(2, getRequestedFor(urlEqualTo(path)))
    }

    "must retry a gateway timeout and succeed on the next request" in {
      val path = "/upscan/retry-timeout"

      stubFor(
        get(urlEqualTo(path))
          .inScenario(upscanTimeoutScenario)
          .whenScenarioStateIs(Scenario.STARTED)
          .willReturn(aResponse().withStatus(OK).withFixedDelay(1000).withBody(fileContent))
          .willSetStateTo(recoveredState)
      )
      stubFor(
        get(urlEqualTo(path))
          .inScenario(upscanTimeoutScenario)
          .whenScenarioStateIs(recoveredState)
          .willReturn(aResponse().withStatus(OK).withBody(fileContent))
      )

      val connector = new UpscanConnector(inject[HttpClientV2], retryConfig, inject[ActorSystem])
      val result    = connector
        .downloadFile(s"$wireMockUrl$path")
        .futureValue
        .runWith(Sink.fold(ByteString.empty)(_ ++ _))
        .futureValue

      result mustBe ByteString(fileContent)
      verify(2, getRequestedFor(urlEqualTo(path)))
    }
  }

  private def md5Base64(value: String): String =
    Base64.getEncoder.encodeToString(MessageDigest.getInstance("MD5").digest(value.getBytes("UTF-8")))
}
