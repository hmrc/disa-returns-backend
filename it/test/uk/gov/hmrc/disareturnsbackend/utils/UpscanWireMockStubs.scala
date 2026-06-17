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
import org.scalatest.Assertions.fail
import uk.gov.hmrc.http.test.WireMockSupport

import java.nio.file.{Files, Paths}

trait UpscanWireMockStubs { self: WireMockSupport =>

  protected def stubUpscanDownload(path: String, resourcePath: String, contentType: String): String = {
    val bytes = readResourceBytes(resourcePath)

    stubUpscanDownload(path, bytes, contentType)
  }

  protected def stubUpscanDownload(path: String, bytes: Array[Byte], contentType: String): String = {

    stubFor(
      get(urlEqualTo(path))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", contentType)
            .withBody(bytes)
        )
    )

    s"$wireMockUrl$path"
  }

  protected def readResourceBytes(resourcePath: String): Array[Byte] = {
    Option(getClass.getClassLoader.getResourceAsStream(resourcePath)) match {
      case Some(input) =>
        try input.readAllBytes()
        finally input.close()
      case None        =>
        val path = Paths.get("it", "resources", resourcePath)
        if (Files.exists(path)) Files.readAllBytes(path)
        else fail(s"Resource [$resourcePath] was not found")
    }
  }
}
