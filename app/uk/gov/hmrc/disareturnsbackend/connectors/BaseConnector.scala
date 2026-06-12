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

import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import org.apache.pekko.util.ByteString
import play.api.http.Status.OK
import uk.gov.hmrc.http.{HttpResponse, Retries, UpstreamErrorResponse}
import uk.gov.hmrc.http.client.{RequestBuilder, readStreamHttpResponse}

import scala.concurrent.{ExecutionContext, Future}

trait BaseConnector extends Retries {

  extension (requestBuilder: RequestBuilder) {
    def executeAsStream(using m: Materializer, ec: ExecutionContext): Future[Source[ByteString, ?]] =
      requestBuilder
        .stream[HttpResponse]
        .flatMap { response =>
          response.status match {
            case OK => Future.successful(response.bodyAsSource)
            case _  => response.errorFromStream
          }
        }
  }

  extension (response: HttpResponse) {
    private def errorFromStream[A](using m: Materializer, ec: ExecutionContext): Future[A] =
      response.bodyAsSource
        .reduce(_ ++ _)
        .map(_.utf8String)
        .runWith(Sink.head)
        .flatMap { result =>
          Future.failed(UpstreamErrorResponse(result, response.status))
        }
  }

  protected def retryCondition: PartialFunction[Exception, Boolean] = {
    case UpstreamErrorResponse.Upstream5xxResponse(_) =>
      true
  }
}
