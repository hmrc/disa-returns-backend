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

import org.apache.pekko.stream.scaladsl.FileIO
import play.api.Logging
import play.api.libs.ws.DefaultBodyWritables.writableOf_Source
import uk.gov.hmrc.disareturnsbackend.config.AppConfig
import uk.gov.hmrc.http.HttpReads.Implicits.readRaw
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}

import java.nio.file.Path
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ObjectStoreConnector @Inject() (
    http: HttpClientV2,
    appConfig: AppConfig
)(implicit ec: ExecutionContext)
    extends Logging {

  def getObject(filename: String)(implicit hc: HeaderCarrier): Future[HttpResponse] =
    http
      .get(url"${appConfig.objectStoreUrl}/object")
      .transform(_.withQueryStringParameters("filename" -> filename))
      .execute[HttpResponse]
      .recoverWith { case exception =>
        logger.warn(
          s"[ObjectStoreConnector][getObject] Failed to retrieve file. Reason: ${exception.getMessage}",
          exception
        )
        Future.failed(exception)
      }
  
  def putObject(file: Path, contentType: String)(implicit hc: HeaderCarrier): Future[HttpResponse] =
    http
      .put(url"${appConfig.objectStoreUrl}/object")
      .setHeader("Content-Type" -> contentType)
      .withBody(FileIO.fromPath(file))
      .transform(_.withRequestTimeout(appConfig.csvProcessingJobRequestTimeout))
      .execute[HttpResponse]
      .recoverWith { case exception =>
        logger.warn(
          s"[ObjectStoreConnector][putObject] Failed to store file. Reason: ${exception.getMessage}",
          exception
        )
        Future.failed(exception)
      }
}
