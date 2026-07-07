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
import uk.gov.hmrc.disareturnsbackend.models.ValidatedMonthlyReturnRequest
import uk.gov.hmrc.disareturnsbackend.validators.ValidationHelper

import java.time.{Clock, LocalDate, YearMonth}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ValidatedMonthlyReturnAction @Inject() (cc: ControllerComponents, clock: Clock)(implicit ec: ExecutionContext)
    extends Results
    with Logging {

  def apply(
    zReference: String,
    taxYear: String,
    month: String,
    checkPeriod: Boolean = false
  ): ActionBuilder[ValidatedMonthlyReturnRequest, AnyContent] =
    new ActionBuilder[ValidatedMonthlyReturnRequest, AnyContent] {
      override def parser: BodyParser[AnyContent] = cc.parsers.defaultBodyParser

      override protected def executionContext: ExecutionContext = ec

      override def invokeBlock[A](
        request: Request[A],
        block: ValidatedMonthlyReturnRequest[A] => Future[Result]
      ): Future[Result] =
        ValidationHelper.validateParams(zReference, taxYear, month) match {
          case Right((validZReference, validTaxYear, validMonth)) =>
            if (!checkPeriod || isPreviousMonthlyPeriod(validTaxYear, validMonth)) {
              block(ValidatedMonthlyReturnRequest(validZReference, validTaxYear, validMonth, request))
            } else {
              logger.warn(
                s"[ValidatedMonthlyReturnAction] Monthly return request is outside the allowed period for zReference [$validZReference], taxYear [$validTaxYear], month [$validMonth]"
              )
              Future.successful(UnprocessableEntity)
            }

          case Left(errorMessage) =>
            logger.warn(
              s"[ValidatedMonthlyReturnAction] Invalid monthly return request parameters for zReference [$zReference], taxYear [$taxYear], month [$month]: [$errorMessage]"
            )
            Future.successful(BadRequest(Json.obj("message" -> errorMessage)))
        }
    }

  private def isPreviousMonthlyPeriod(taxYear: String, month: Int): Boolean = {
    val previousMonth = YearMonth.from(LocalDate.now(clock)).minusMonths(1)

    taxYear == taxYearFor(previousMonth) && month == previousMonth.getMonthValue
  }

  private def taxYearFor(period: YearMonth): String = {
    val startYear = if (period.getMonthValue >= 4) period.getYear else period.getYear - 1

    f"$startYear%04d-${(startYear + 1) % 100}%02d"
  }
}
