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
import play.api.http.Status.{CONFLICT, CREATED, UNPROCESSABLE_ENTITY}
import play.api.libs.json.Json
import play.api.libs.ws.writeableOf_JsValue
import uk.gov.hmrc.disareturnsbackend.config.AppConfig
import uk.gov.hmrc.disareturnsbackend.connectors.ReturnsSubmissionConnector.CreateMonthlyReturnSubmissionResult
import uk.gov.hmrc.disareturnsbackend.connectors.ReturnsSubmissionConnector.CreateMonthlyReturnSubmissionResult.*
import uk.gov.hmrc.disareturnsbackend.models.CreateMonthlyReturnResponse
import uk.gov.hmrc.http.HttpReads.Implicits.readRaw
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps, UpstreamErrorResponse}

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ReturnsSubmissionConnector @Inject() (
  httpClient: HttpClientV2,
  appConfig: AppConfig,
  override val configuration: Config,
  override val actorSystem: ActorSystem
) extends BaseConnector {

  def createMonthlyReturn(
    zReference: String,
    taxYear: String,
    month: Int,
    nilReturn: Boolean
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[CreateMonthlyReturnSubmissionResult] =
    retryFor[CreateMonthlyReturnSubmissionResult]("create monthly return in disa-returns-submission")(retryCondition) {
      httpClient
        .post(url"${appConfig.returnsSubmissionService}/disa-returns-submission/monthly/$zReference/$taxYear/$month")
        .withBody(Json.obj("nilReturn" -> nilReturn))
        .execute
        .flatMap { response =>
          response.status match {
            case CREATED              => Future.successful(Created(readSubmissionId(response.json.as[CreateMonthlyReturnResponse])))
            case CONFLICT             =>
              Future.successful(AlreadyExists(readSubmissionId(response.json.as[CreateMonthlyReturnResponse])))
            case UNPROCESSABLE_ENTITY => Future.successful(OutsideDeclarationPeriod)
            case status               => Future.failed(UpstreamErrorResponse(response.body, status))
          }
        }
    }

  private def readSubmissionId(response: CreateMonthlyReturnResponse): UUID =
    response.submissionId
}

object ReturnsSubmissionConnector {

  sealed trait CreateMonthlyReturnSubmissionResult

  object CreateMonthlyReturnSubmissionResult {
    final case class Created(submissionId: UUID) extends CreateMonthlyReturnSubmissionResult
    final case class AlreadyExists(submissionId: UUID) extends CreateMonthlyReturnSubmissionResult
    case object OutsideDeclarationPeriod extends CreateMonthlyReturnSubmissionResult
  }
}
