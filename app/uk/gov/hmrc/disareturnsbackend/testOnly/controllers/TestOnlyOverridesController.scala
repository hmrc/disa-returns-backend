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

package uk.gov.hmrc.disareturnsbackend.testOnly.controllers

import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}
import uk.gov.hmrc.disareturnsbackend.testOnly.connectors.TestOnlyReturnsSubmissionConnector
import uk.gov.hmrc.disareturnsbackend.testOnly.models.{TestOverride, TestOverrideRequest}
import uk.gov.hmrc.disareturnsbackend.validators.ZReferenceValidator
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import java.util.Locale
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class TestOnlyOverridesController @Inject() (
  cc: ControllerComponents,
  connector: TestOnlyReturnsSubmissionConnector
)(implicit ec: ExecutionContext)
    extends BackendController(cc) {

  def get(zReference: String): Action[AnyContent] = Action.async { implicit request =>
    withZReference(zReference) { normalized =>
      implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)
      asResult(connector.getOverrides(normalized))
    }
  }

  def set(zReference: String): Action[TestOverrideRequest] =
    Action.async(parse.json[TestOverrideRequest]) { implicit request =>
      withZReference(zReference) { normalized =>
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)
        asResult(connector.setOverrides(normalized, request.body))
      }
    }

  def delete(zReference: String): Action[AnyContent] = Action.async { implicit request =>
    withZReference(zReference) { normalized =>
      implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)
      asResult(connector.deleteOverrides(normalized))
    }
  }

  private def withZReference(zReference: String)(action: String => Future[Result]): Future[Result] = {
    val normalized = zReference.trim.toUpperCase(Locale.ROOT)
    if (ZReferenceValidator.isValid(normalized)) {
      action(normalized)
    } else {
      Future.successful(BadRequest(Json.obj("message" -> "invalid zReference")))
    }
  }

  private def asResult(response: Future[TestOverride]): Future[Result] =
    response.map(value => Ok(Json.toJson(value))).recover { case NonFatal(_) => ServiceUnavailable }
}
