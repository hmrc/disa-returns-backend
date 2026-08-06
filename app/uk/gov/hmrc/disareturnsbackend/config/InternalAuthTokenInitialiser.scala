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

package uk.gov.hmrc.disareturnsbackend.config

import com.typesafe.config.Config
import org.apache.pekko.Done
import org.apache.pekko.actor.ActorSystem
import play.api.Logging
import play.api.http.Status.CREATED
import play.api.libs.concurrent.Futures
import play.api.libs.json.Json
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue
import uk.gov.hmrc.http.HttpReads.Implicits.{readEitherOf, readRaw}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{BadGatewayException, GatewayTimeoutException, HeaderCarrier, HttpResponse, Retries, StringContextOps, UpstreamErrorResponse}

import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

abstract class InternalAuthTokenInitialiser {
  final lazy val initialised: Future[Done] = initialise()

  protected def initialise(): Future[Done]
}

@Singleton
class NoOpInternalAuthTokenInitialiser @Inject() extends InternalAuthTokenInitialiser {
  override protected def initialise(): Future[Done] = Future.successful(Done)
}

@Singleton
class InternalAuthTokenInitialiserImpl @Inject() (
  val actorSystem: ActorSystem,
  config: AppConfig,
  val configuration: Config,
  httpClient: HttpClientV2,
  futures: Futures
)(implicit ec: ExecutionContext)
    extends InternalAuthTokenInitialiser
    with Logging
    with Retries {

  override protected def initialise(): Future[Done] =
    futures.timeout(30.seconds)(ensureAuthToken())

  private def ensureAuthToken(): Future[Done] =
    createClientAuthToken()

  private def createClientAuthToken(): Future[Done] = {
    logger.info(
      "[InternalAuthTokenInitialiser][createClientAuthToken] Initialising auth token"
    )
    retryFor("POST Initialise Auth token") {
      case _: BadGatewayException | _: GatewayTimeoutException => true
      case UpstreamErrorResponse.Upstream4xxResponse(_)        => true
      case UpstreamErrorResponse.Upstream5xxResponse(_)        => true
    } {
      httpClient
        .post(url"${config.internalAuthService}/test-only/token")(HeaderCarrier())
        .withBody(
          Json.obj(
            "token"       -> config.internalAuthToken,
            "principal"   -> config.appName,
            "permissions" -> Seq(
              Json.obj(
                "resourceType"     -> "object-store",
                "resourceLocation" -> "disa-returns-backend",
                "actions"          -> List("READ", "WRITE", "DELETE")
              ),
              Json.obj(
                "resourceType"     -> "disa-returns-submission",
                "resourceLocation" -> "*",
                "actions"          -> List("READ", "WRITE")
              )
            )
          )
        )
        .execute[Either[UpstreamErrorResponse, HttpResponse]]
        .flatMap {
          case Right(response) if response.status == CREATED =>
            logger.info(
              "[InternalAuthTokenInitialiser][createClientAuthToken] Auth token initialised"
            )
            Future.successful(Done)

          case Left(error) =>
            logger.warn(
              "[InternalAuthTokenInitialiser][createClientAuthToken] Unable to initialise internal-auth token, retrying..."
            )
            Future.failed(error)

          case Right(_) =>
            logger.error(
              "[InternalAuthTokenInitialiser][createClientAuthToken] Failed to initialise internal-auth token"
            )
            Future.failed(
              new RuntimeException("Failed to initialise internal-auth token")
            )
        }
    }
  }

}
