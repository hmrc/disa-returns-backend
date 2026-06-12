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
import org.apache.pekko.stream.scaladsl.FileIO
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.objectstore.client.http.Payload
import uk.gov.hmrc.objectstore.client.play.PlayObjectStoreClient
import uk.gov.hmrc.objectstore.client.{Md5Hash, Path}

import java.nio.file.{Files, Path as FilePath}
import java.security.MessageDigest
import java.util.Base64
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future, blocking}
import scala.util.Using

@Singleton
class ObjectStoreConnector @Inject() (
  client: PlayObjectStoreClient,
  override val actorSystem: ActorSystem,
  override val configuration: Config
)(implicit ec: ExecutionContext)
    extends BaseConnector {

  import uk.gov.hmrc.objectstore.client.play.Implicits.*

  private implicit val blockingExecutionContext: ExecutionContextExecutor =
    actorSystem.dispatchers.lookup("contexts.file-upload-blocking")

  def putFile(
    objectName: String,
    file: FilePath,
    contentType: String
  )(implicit hc: HeaderCarrier): Future[String] =
    Future {
      blocking {
        val length = Files.size(file)
        val md5    = md5Base64(file)
        length -> md5
      }
    }(blockingExecutionContext).flatMap { case (length, md5) =>
      retryFor[String]("put object-store file")(retryCondition) {
        client
          .putObject(
            path = Path.Directory("").file(objectName),
            content = Payload(
              length = length,
              md5Hash = md5,
              content = FileIO.fromPath(file)
            ),
            contentType = Some(contentType)
          )
          .map(_.location.asUri)
      }
    }(ec)

  private def md5Base64(file: FilePath): Md5Hash = {
    val digest = MessageDigest.getInstance("MD5")
    val buffer = new Array[Byte](8192)

    Using.resource(Files.newInputStream(file)) { input =>
      var read = input.read(buffer)

      while (read != -1) {
        digest.update(buffer, 0, read)
        read = input.read(buffer)
      }
    }

    Md5Hash(Base64.getEncoder.encodeToString(digest.digest()))
  }
}
