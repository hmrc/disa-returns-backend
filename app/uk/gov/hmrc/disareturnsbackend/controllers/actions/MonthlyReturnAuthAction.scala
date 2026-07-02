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

package uk.gov.hmrc.disareturnsbackend.controllers.actions

import play.api.Logging
import play.api.libs.json.Json
import play.api.mvc.{ActionBuilder, AnyContent, BodyParser, ControllerComponents, Request, Result, Results}
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisationException, AuthorisedFunctions, Enrolments, InternalError, NoActiveSession}
import uk.gov.hmrc.disareturnsbackend.models.ValidatedMonthlyReturnRequest
import uk.gov.hmrc.disareturnsbackend.validators.ValidationHelper
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

trait MonthlyReturnAuthAction {

  def apply(
    zReference: String,
    taxYear: String,
    month: String
  ): ActionBuilder[ValidatedMonthlyReturnRequest, AnyContent]
}

@Singleton
class MonthlyReturnAuthActionImpl @Inject() (cc: ControllerComponents, authConnector: AuthConnector)(implicit
  ec: ExecutionContext
) extends MonthlyReturnAuthAction
    with Results
    with Logging {

  private val enrolmentKey  = "HMRC-DISA-ORG"
  private val identifierKey = "ZREF"

  private val authFunctions = new AuthorisedFunctions {
    override def authConnector: AuthConnector = MonthlyReturnAuthActionImpl.this.authConnector
  }

  override def apply(
    zReference: String,
    taxYear: String,
    month: String
  ): ActionBuilder[ValidatedMonthlyReturnRequest, AnyContent] =
    new ActionBuilder[ValidatedMonthlyReturnRequest, AnyContent] {
      override def parser: BodyParser[AnyContent] = cc.parsers.defaultBodyParser

      override protected def executionContext: ExecutionContext = ec

      override def invokeBlock[A](
        request: Request[A],
        block: ValidatedMonthlyReturnRequest[A] => Future[Result]
      ): Future[Result] = {
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

        authFunctions
          .authorised()
          .retrieve(Retrievals.allEnrolments) { enrolments =>
            ValidationHelper.validateParams(zReference, taxYear, month) match {
              case Right((validZReference, validTaxYear, validMonth)) =>
                if (hasMatchingDisaEnrolment(enrolments, validZReference)) {
                  block(ValidatedMonthlyReturnRequest(validZReference, validTaxYear, validMonth, request))
                } else {
                  logger.warn(
                    s"[MonthlyReturnAuthAction] DISA enrolment [$enrolmentKey/$identifierKey] does not match zReference [$validZReference]"
                  )
                  Future.successful(Forbidden)
                }

              case Left(errorMessage) =>
                logger.warn(
                  s"[MonthlyReturnAuthAction] Invalid monthly return request parameters for zReference [$zReference], taxYear [$taxYear], month [$month]: [$errorMessage]"
                )
                Future.successful(BadRequest(Json.obj("message" -> errorMessage)))
            }
          }
          .recover {
            case exception: InternalError =>
              logger.error("[MonthlyReturnAuthAction] Auth request failed with internal error", exception)
              ServiceUnavailable

            case exception: NoActiveSession =>
              logger.warn("[MonthlyReturnAuthAction] Bearer token is missing or invalid", exception)
              Unauthorized

            case exception: AuthorisationException =>
              logger.warn("[MonthlyReturnAuthAction] Authorisation failed", exception)
              Unauthorized

            case NonFatal(exception) =>
              logger.error("[MonthlyReturnAuthAction] Auth request failed unexpectedly", exception)
              ServiceUnavailable
          }
      }
    }

  private def hasMatchingDisaEnrolment(enrolments: Enrolments, zReference: String): Boolean =
    enrolments
      .getEnrolment(enrolmentKey)
      .filter(_.isActivated)
      .exists(
        _.getIdentifier(identifierKey)
          .exists(identifier => identifier.value.equalsIgnoreCase(zReference))
      )
}
