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

import com.typesafe.config.Config
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}

import javax.inject.*
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UpscanConnector @Inject() (
  httpClient: HttpClientV2,
  override val configuration: Config,
  override val actorSystem: ActorSystem
) extends BaseConnector {

  def downloadFile(
    downloadUrl: String
  )(implicit hc: HeaderCarrier, ec: ExecutionContext, materializer: Materializer): Future[Source[ByteString, ?]] =
    retryFor[Source[ByteString, ?]]("download upscan file")(retryCondition) {
      httpClient
        .get(url"$downloadUrl")
        .executeAsStream
    }
}
