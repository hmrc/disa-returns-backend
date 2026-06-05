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

package uk.gov.hmrc.disareturnsbackend.controllers

import play.api.Logging
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.disareturnsbackend.models.UpscanResult
import uk.gov.hmrc.disareturnsbackend.services.UpscanCallbackService
import uk.gov.hmrc.disareturnsbackend.validators.ValidationHelper
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.play.bootstrap.controller.WithJsonBody

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class UpscanCallbackController @Inject() (
  cc: ControllerComponents,
  upscanCallbackService: UpscanCallbackService
)(implicit ec: ExecutionContext)
    extends BackendController(cc)
    with WithJsonBody
    with Logging {

  def monthlyReturnUpscanCallback(zReference: String, taxYear: String, month: String): Action[JsValue] =
    Action.async(parse.json) { implicit request =>
      ValidationHelper.validateParams(zReference, taxYear, month) match {
        case Right((validZReference, validTaxYear, validMonth)) =>
          withJsonBody[UpscanResult] { upscanResult =>
            logger.info(
              s"[UpscanCallbackController][monthlyReturnUpscanCallback] Upscan callback for zReference [$validZReference], taxYear [$validTaxYear], month [$validMonth], upload reference [${upscanResult.reference}]"
            )
            upscanCallbackService
              .monthlyReturnUpscanCallback(validZReference, validTaxYear, validMonth, upscanResult)
              .map(_ => Accepted)
              .recover { case NonFatal(_) =>
                ServiceUnavailable
              }
          }

        case Left(errorMessage) =>
          logger.warn(
            s"[UpscanCallbackController][monthlyReturnUpscanCallback] Invalid upscan callback parameters for zReference [$zReference], taxYear [$taxYear], month [$month]: [$errorMessage]"
          )
          Future.successful(BadRequest(Json.obj("message" -> errorMessage)))
      }
    }
}
