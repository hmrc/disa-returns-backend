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

import play.api.http.HeaderNames.AUTHORIZATION
import play.api.http.Status.OK
import uk.gov.hmrc.disareturnsbackend.config.AppConfig
import uk.gov.hmrc.disareturnsbackend.validators.ZReferenceValidator
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps, UpstreamErrorResponse}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DisaAccountConnector @Inject() (
  httpClient: HttpClientV2,
  appConfig: AppConfig
)(implicit ec: ExecutionContext) {

  def getAgentZReference()(implicit hc: HeaderCarrier): Future[String] =
    httpClient
      .get(url"${appConfig.disaAccountService}/disa-account/agents/preferences")
      .setHeader(hc.authorization.map(authorization => AUTHORIZATION -> authorization.value).toSeq*)
      .execute[HttpResponse]
      .map { response =>
        response.status match {
          case OK     =>
            val zReference = (response.json \ "zReference").as[String]
            require(ZReferenceValidator.isValid(zReference), "Invalid ZReference in agent preferences response")
            zReference
          case status =>
            throw UpstreamErrorResponse("Failed to retrieve disa-account agent preferences", status)
        }
      }
}
