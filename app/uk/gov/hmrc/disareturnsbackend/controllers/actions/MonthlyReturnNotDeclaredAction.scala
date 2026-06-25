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
import play.api.mvc.{ActionFilter, Result, Results}
import uk.gov.hmrc.disareturnsbackend.connectors.ReturnsSubmissionConnector
import uk.gov.hmrc.disareturnsbackend.models.ValidatedMonthlyReturnRequest
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class MonthlyReturnNotDeclaredAction @Inject() (returnsSubmissionConnector: ReturnsSubmissionConnector)(implicit
  ec: ExecutionContext
) extends Results
    with Logging {

  def apply(resultIfDeclared: Result): ActionFilter[ValidatedMonthlyReturnRequest] =
    new ActionFilter[ValidatedMonthlyReturnRequest] {
      override protected def executionContext: ExecutionContext = ec

      override protected def filter[A](request: ValidatedMonthlyReturnRequest[A]): Future[Option[Result]] = {
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

        returnsSubmissionConnector
          .getMonthlyReturn(request.zReference, request.taxYear, request.month)
          .map {
            case Some(monthlyReturn) if (monthlyReturn \ "declaredOn").isDefined =>
              Some(resultIfDeclared)

            case _ =>
              None
          }
          .recover { case NonFatal(exception) =>
            logger.error(
              s"[MonthlyReturnNotDeclaredAction] Failed to check declaration in disa-returns-submission for zReference [${request.zReference}], taxYear [${request.taxYear}], month [${request.month}]",
              exception
            )
            Some(ServiceUnavailable)
          }
      }
    }
}
