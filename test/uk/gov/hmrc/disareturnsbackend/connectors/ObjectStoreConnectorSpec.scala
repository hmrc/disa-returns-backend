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
import com.typesafe.config.{Config, ConfigFactory}
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import org.apache.pekko.util.ByteString
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import play.api.http.Status.INTERNAL_SERVER_ERROR
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.objectstore.client.http.Payload
import uk.gov.hmrc.objectstore.client.play.PlayObjectStoreClient
import uk.gov.hmrc.objectstore.client.{Md5Hash, ObjectSummaryWithMd5, Path}

import java.nio.file.Files
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import scala.concurrent.Future

class ObjectStoreConnectorSpec extends SpecBase {

  private implicit val hc: HeaderCarrier = HeaderCarrier()
  private val callAmountWithRetries: Int = 4
  private val retryConfig: Config        =
    ConfigFactory.parseString("http-verbs.retries.intervals = [1 millisecond, 1 millisecond, 1 millisecond]")

  "ObjectStoreConnector" - {

    "must upload the file as a streaming Payload and return the object-store location" in {
      val client                                                        = mock[PlayObjectStoreClient]
      val file                                                          = Files.createTempFile("disa-object-store", ".txt")
      Files.writeString(file, "file-content")
      val summaryLocation                                               = Path.Directory("").file("uploaded-file")
      val payloadCaptor: ArgumentCaptor[Payload[Source[ByteString, ?]]] =
        ArgumentCaptor.forClass(classOf[Payload[Source[ByteString, ?]]])

      when(client.putObject(any, any[Payload[Source[ByteString, ?]]], any, any, any, any)(any, any))
        .thenReturn(
          Future.successful(ObjectSummaryWithMd5(summaryLocation, 12L, Md5Hash(md5Base64("file-content")), Instant.now))
        )

      try {
        val service = new ObjectStoreConnector(client, inject[ActorSystem], retryConfig)

        service.putFile("object-name", file, "text/plain").futureValue mustBe summaryLocation.asUri

        verify(client).putObject(any, payloadCaptor.capture(), any, any, any, any)(any, any)
        val payload = payloadCaptor.getValue
        payload.length mustBe 12L
        payload.md5Hash mustBe Md5Hash(md5Base64("file-content"))
        payload.content
          .runWith(Sink.fold(ByteString.empty)(_ ++ _))(inject[Materializer])
          .futureValue mustBe ByteString("file-content")
      } finally Files.deleteIfExists(file)
    }

    "must return a failed Future, not throw eagerly, when file metadata cannot be read" in {
      val client      = mock[PlayObjectStoreClient]
      val service     = new ObjectStoreConnector(client, inject[ActorSystem], retryConfig)
      val missingFile = Files.createTempDirectory("disa-missing-parent").resolve("missing.txt")

      val result = service.putFile("object-name", missingFile, "text/plain")

      result.failed.futureValue mustBe a[java.nio.file.NoSuchFileException]
      Files.deleteIfExists(missingFile.getParent)
    }

    "must retry when object-store returns a 5xx error" in {
      val client       = mock[PlayObjectStoreClient]
      val service      = new ObjectStoreConnector(client, inject[ActorSystem], retryConfig)
      val file         = Files.createTempFile("disa-object-store", ".txt")
      Files.writeString(file, "file-content")
      val errorMessage = "object-store upload failed"

      when(client.putObject(any, any[Payload[Source[ByteString, ?]]], any, any, any, any)(any, any))
        .thenReturn(Future.failed(UpstreamErrorResponse(errorMessage, INTERNAL_SERVER_ERROR)))

      try {
        val result = service.putFile("object-name", file, "text/plain").failed.futureValue

        result mustBe UpstreamErrorResponse(errorMessage, INTERNAL_SERVER_ERROR)
        verify(client, times(callAmountWithRetries))
          .putObject(any, any[Payload[Source[ByteString, ?]]], any, any, any, any)(any, any)
      } finally Files.deleteIfExists(file)
    }
  }

  private def md5Base64(value: String): String = {
    val digest = MessageDigest.getInstance("MD5").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))
    Base64.getEncoder.encodeToString(digest)
  }
}
