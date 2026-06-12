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
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.{reset, times, verify, when}
import play.api.http.Status.{INTERNAL_SERVER_ERROR, OK}
import uk.gov.hmrc.http.client.{HttpClientV2, RequestBuilder}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps, UpstreamErrorResponse}

import scala.concurrent.Future

class UpscanConnectorSpec extends SpecBase {

  private implicit lazy val materializer: Materializer = app.materializer
  private implicit val hc: HeaderCarrier               = HeaderCarrier()

  private val callAmountWithRetries: Int         = 4
  private val mockHttpClient: HttpClientV2       = mock[HttpClientV2]
  private val mockRequestBuilder: RequestBuilder = mock[RequestBuilder]
  private val retryConfig: Config                =
    ConfigFactory.parseString("http-verbs.retries.intervals = [1 millisecond, 1 millisecond, 1 millisecond]")
  private val connector: UpscanConnector         = new UpscanConnector(mockHttpClient, retryConfig, inject[ActorSystem])
  private val downloadUrl: String                = testDownloadUrl

  "UpscanConnector" - {

    "downloadFile" - {

      "must return the file stream when the upscan download succeeds" in {
        reset(mockHttpClient, mockRequestBuilder)

        val fileContents = "test-file-contents"
        val source       = Source.single(ByteString(fileContents))
        val response     = mock[HttpResponse]

        when(response.status).thenReturn(OK)
        when(response.bodyAsSource).thenReturn(source)
        when(mockHttpClient.get(eqTo(url"$downloadUrl"))(any())).thenReturn(mockRequestBuilder)
        when(mockRequestBuilder.stream[HttpResponse](using any(), any()))
          .thenReturn(Future.successful(response))

        val result = connector
          .downloadFile(downloadUrl)
          .futureValue
          .runFold(ByteString.empty)(_ ++ _)
          .futureValue
          .utf8String

        result mustBe fileContents
      }

      "must propagate an upstream error when the upscan download fails" in {
        reset(mockHttpClient, mockRequestBuilder)

        val errorMessage = "upscan download failed"
        val response     = mock[HttpResponse]

        when(response.status).thenReturn(INTERNAL_SERVER_ERROR)
        when(response.bodyAsSource).thenReturn(Source(List(ByteString("upscan "), ByteString("download failed"))))
        when(mockHttpClient.get(eqTo(url"$downloadUrl"))(any())).thenReturn(mockRequestBuilder)
        when(mockRequestBuilder.stream[HttpResponse](using any(), any()))
          .thenReturn(Future.successful(response))

        val result = connector.downloadFile(downloadUrl).failed.futureValue

        result mustBe UpstreamErrorResponse(errorMessage, INTERNAL_SERVER_ERROR)
        verify(mockRequestBuilder, times(callAmountWithRetries)).stream[HttpResponse](using any(), any())
      }
    }
  }
}
