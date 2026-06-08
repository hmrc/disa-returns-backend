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

import play.api.Logging
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.disareturnsbackend.repositories.MonthlyReturnRepository
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

@Singleton
class TestOnlyMonthlyReturnController @Inject() (
  cc: ControllerComponents,
  monthlyReturnRepository: MonthlyReturnRepository
)(implicit ec: ExecutionContext)
    extends BackendController(cc)
    with Logging {

  def deleteAll(): Action[AnyContent] = Action.async {
    monthlyReturnRepository
      .deleteAll()
      .map { deletedCount =>
        logger.info(s"[TestOnlyMonthlyReturnController][deleteAll] Deleted [$deletedCount] monthly returns")
        NoContent
      }
      .recover { case NonFatal(exception) =>
        logger.error("[TestOnlyMonthlyReturnController][deleteAll] Failed to delete monthly returns", exception)
        ServiceUnavailable
      }
  }
}
