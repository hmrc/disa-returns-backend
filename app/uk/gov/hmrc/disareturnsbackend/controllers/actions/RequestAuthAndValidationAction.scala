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
import play.api.http.Status.{FORBIDDEN, NOT_FOUND, UNAUTHORIZED}
import play.api.libs.json.Json
import play.api.mvc.*
import uk.gov.hmrc.auth.core.*
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.disareturnsbackend.config.AppConfig
import uk.gov.hmrc.disareturnsbackend.connectors.DisaAccountConnector
import uk.gov.hmrc.disareturnsbackend.models.*
import uk.gov.hmrc.disareturnsbackend.utils.TimeSource
import uk.gov.hmrc.disareturnsbackend.validators.*
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import java.time.*
import javax.inject.*
import scala.concurrent.*
import scala.util.control.NonFatal

trait RequestAuthAndValidationAction {

  def apply(
    zReference: String,
    taxYear: String,
    month: String
  ): ActionBuilder[ValidatedMonthlyReturnRequest, AnyContent] =
    apply(zReference, taxYear, month, checkPeriod = false)

  def apply(
    zReference: String,
    taxYear: String,
    month: String,
    checkPeriod: Boolean
  ): ActionBuilder[ValidatedMonthlyReturnRequest, AnyContent]
}

trait RequestAuthAction {
  def apply(zReference: String): ActionBuilder[ValidatedZReferenceRequest, AnyContent]
}

@Singleton
class RequestAuthAndValidationActionImpl @Inject() (
  cc: ControllerComponents,
  authConnector: AuthConnector,
  timeSource: TimeSource,
  appConfig: AppConfig,
  disaAccountConnector: DisaAccountConnector
)(implicit
  ec: ExecutionContext
) extends RequestAuthAndValidationAction
    with RequestAuthAction
    with Results
    with Logging {

  private val enrolmentKey  = "HMRC-DISA-ORG"
  private val identifierKey = "ZREF"

  private val authFunctions = new AuthorisedFunctions {
    override def authConnector: AuthConnector = RequestAuthAndValidationActionImpl.this.authConnector
  }

  override def apply(
    zReference: String,
    taxYear: String,
    month: String,
    checkPeriod: Boolean
  ): ActionBuilder[ValidatedMonthlyReturnRequest, AnyContent] =
    new ActionBuilder[ValidatedMonthlyReturnRequest, AnyContent] {
      override def parser: BodyParser[AnyContent] = cc.parsers.defaultBodyParser

      override protected def executionContext: ExecutionContext = ec

      override def invokeBlock[A](
        request: Request[A],
        block: ValidatedMonthlyReturnRequest[A] => Future[Result]
      ): Future[Result] =
        authorised(request) { (enrolments, affinityGroup, hc) =>
          implicit val headerCarrier: HeaderCarrier = hc
          ValidationHelper.validateParams(zReference, taxYear, month) match {
            case Right((validZReference, validTaxYear, validMonth)) =>
              isPreviousMonthlyPeriod(validZReference, validTaxYear, validMonth, checkPeriod).flatMap { periodIsValid =>
                if (!periodIsValid) {
                  logger.warn(
                    s"[RequestAuthAndValidationAction] Monthly return request is outside the allowed period for zReference [$validZReference], taxYear [$validTaxYear], month [$validMonth]"
                  )
                  Future.successful(UnprocessableEntity)
                } else {
                  authoriseZReference(enrolments, affinityGroup, validZReference) {
                    block(ValidatedMonthlyReturnRequest(validZReference, validTaxYear, validMonth, request))
                  }
                }
              }

            case Left(errorMessage) =>
              logger.warn(
                s"[RequestAuthAndValidationAction] Invalid monthly return request parameters for zReference [$zReference], taxYear [$taxYear], month [$month]: [$errorMessage]"
              )
              Future.successful(BadRequest(Json.obj("message" -> errorMessage)))
          }
        }
    }

  override def apply(zReference: String): ActionBuilder[ValidatedZReferenceRequest, AnyContent] =
    new ActionBuilder[ValidatedZReferenceRequest, AnyContent] {
      override def parser: BodyParser[AnyContent] = cc.parsers.defaultBodyParser

      override protected def executionContext: ExecutionContext = ec

      override def invokeBlock[A](
        request: Request[A],
        block: ValidatedZReferenceRequest[A] => Future[Result]
      ): Future[Result] =
        authorised(request) { (enrolments, affinityGroup, hc) =>
          implicit val headerCarrier: HeaderCarrier = hc
          val normalised                            = zReference.trim.toUpperCase
          if (!ZReferenceValidator.isValid(normalised)) {
            logger.warn(s"[RequestAuthAndValidationAction] Invalid zReference [$zReference]")
            Future.successful(BadRequest(Json.obj("message" -> "Invalid field: [zReference]")))
          } else {
            authoriseZReference(enrolments, affinityGroup, normalised) {
              block(ValidatedZReferenceRequest(normalised, request))
            }
          }
        }
    }

  private def authorised[A](
    request: Request[A]
  )(block: (Enrolments, Option[AffinityGroup], HeaderCarrier) => Future[Result]): Future[Result] = {
    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

    authFunctions
      .authorised()
      .retrieve(Retrievals.allEnrolments and Retrievals.affinityGroup) { case enrolments ~ affinityGroup =>
        block(enrolments, affinityGroup, hc)
      }
      .recover {
        case exception: InternalError =>
          logger.error("[RequestAuthAndValidationAction] Auth request failed with internal error", exception)
          ServiceUnavailable

        case exception: NoActiveSession =>
          logger.warn("[RequestAuthAndValidationAction] Bearer token is missing or invalid", exception)
          Unauthorized

        case exception: AuthorisationException =>
          logger.warn("[RequestAuthAndValidationAction] Authorisation failed", exception)
          Unauthorized

        case NonFatal(exception) =>
          logger.error("[RequestAuthAndValidationAction] Auth request failed unexpectedly", exception)
          ServiceUnavailable
      }
  }

  private def authoriseZReference(
    enrolments: Enrolments,
    affinityGroup: Option[AffinityGroup],
    zReference: String
  )(block: => Future[Result])(implicit hc: HeaderCarrier): Future[Result] =
    affinityGroup match {
      case Some(AffinityGroup.Agent) if !appConfig.agentAuthenticationEnabled =>
        logger.warn("[RequestAuthAndValidationAction] Agent authentication is disabled")
        Future.successful(Forbidden)
      case Some(AffinityGroup.Agent)                                          =>
        disaAccountConnector
          .getAgentZReference()
          .map[Either[Result, String]](Right(_))
          .recover {
            case error: UpstreamErrorResponse if error.statusCode == UNAUTHORIZED =>
              logger.warn("[RequestAuthAndValidationAction] Agent preferences returned 401 Unauthorized", error)
              Left(Unauthorized)
            case error: UpstreamErrorResponse if error.statusCode == FORBIDDEN    =>
              logger.warn(
                "[RequestAuthAndValidationAction] Agent preferences returned 403 Forbidden: authorisation denied",
                error
              )
              Left(Forbidden)
            case error: UpstreamErrorResponse if error.statusCode == NOT_FOUND    =>
              logger.warn(
                "[RequestAuthAndValidationAction] Agent preferences returned 404 Not Found: preferences missing or expired",
                error
              )
              Left(Forbidden)
            case NonFatal(error)                                                  =>
              logger.error("[RequestAuthAndValidationAction] Agent preferences request failed", error)
              Left(ServiceUnavailable)
          }
          .flatMap {
            case Left(result)                                                                 => Future.successful(result)
            case Right(selectedZReference) if selectedZReference.equalsIgnoreCase(zReference) => block
            case Right(_)                                                                     =>
              logger.warn("[RequestAuthAndValidationAction] Agent preference does not match the requested ZReference")
              Future.successful(Forbidden)
          }
      case _ if hasMatchingDisaEnrolment(enrolments, zReference)              => block
      case _                                                                  =>
        logger.warn(
          s"[RequestAuthAndValidationAction] DISA enrolment [$enrolmentKey/$identifierKey] does not match zReference [$zReference]"
        )
        Future.successful(Forbidden)
    }

  private def isPreviousMonthlyPeriod(
    zReference: String,
    taxYear: String,
    month: Int,
    checkPeriod: Boolean
  )(implicit hc: HeaderCarrier): Future[Boolean] =
    if (!checkPeriod) {
      Future.successful(true)
    } else {
      timeSource.instant(zReference).map { instant =>
        val previousMonth = YearMonth.from(LocalDate.ofInstant(instant, ZoneOffset.UTC)).minusMonths(1)
        taxYear == taxYearFor(previousMonth) && month == previousMonth.getMonthValue
      }
    }

  private def taxYearFor(period: YearMonth): String = {
    val startYear = if (period.getMonthValue >= 4) period.getYear else period.getYear - 1

    f"$startYear%04d-${(startYear + 1) % 100}%02d"
  }

  private def hasMatchingDisaEnrolment(enrolments: Enrolments, zReference: String): Boolean =
    enrolments.enrolments
      .exists { enrolment =>
        enrolment.key == enrolmentKey &&
        enrolment.isActivated &&
        enrolment
          .getIdentifier(identifierKey)
          .exists(identifier => identifier.value.equalsIgnoreCase(zReference))
      }
}
