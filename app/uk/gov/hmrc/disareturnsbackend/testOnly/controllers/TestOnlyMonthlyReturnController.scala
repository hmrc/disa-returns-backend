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
import play.api.libs.json.JsValue
import play.api.mvc.*
import uk.gov.hmrc.disareturnsbackend.repositories.*
import uk.gov.hmrc.disareturnsbackend.validators.ZReferenceValidator
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import java.util.Locale
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class TestOnlyMonthlyReturnController @Inject() (
  cc: ControllerComponents,
  monthlyReturnRepository: MonthlyReturnRepository,
  fileUploadWorkItems: MonthlyReturnFileUploadWorkItemRepository,
  submissionWorkItems: MonthlyReturnSubmissionWorkItemRepository
)(implicit ec: ExecutionContext)
    extends BackendController(cc)
    with Logging {

  def cleanupAll(): Action[AnyContent] = Action.async {
    cleanupResult("monthly returns and work items") {
      for {
        workItems <- deleteAllWorkItems()
        returns   <- monthlyReturnRepository.deleteAll()
      } yield workItems + returns
    }
  }

  def cleanup(): Action[JsValue] = withZReferences { zReferences =>
    cleanupResult(s"monthly returns and work items for [${zReferences.size}] Z-references") {
      for {
        workItems <- deleteWorkItems(zReferences)
        returns   <- monthlyReturnRepository.deleteByZReferences(zReferences)
      } yield workItems + returns
    }
  }

  def cleanupAllWorkItems(): Action[AnyContent] = Action.async {
    cleanupResult("monthly return work items")(deleteAllWorkItems())
  }

  def cleanupWorkItems(): Action[JsValue] = withZReferences { zReferences =>
    cleanupResult(s"monthly return work items for [${zReferences.size}] Z-references")(deleteWorkItems(zReferences))
  }

  private def deleteAllWorkItems(): Future[Long] =
    for {
      uploads     <- fileUploadWorkItems.deleteAll()
      submissions <- submissionWorkItems.deleteAll()
    } yield uploads + submissions

  private def deleteWorkItems(zReferences: Seq[String]): Future[Long] =
    for {
      uploads     <- fileUploadWorkItems.deleteByZReferences(zReferences)
      submissions <- submissionWorkItems.deleteByZReferences(zReferences)
    } yield uploads + submissions

  private def withZReferences(cleanup: Seq[String] => Future[Result]): Action[JsValue] = Action.async(parse.json) {
    request =>
      val normalized = (request.body \ "zReferences")
        .validate[Seq[String]]
        .asOpt
        .getOrElse(Seq.empty)
        .map(_.trim.toUpperCase(Locale.ROOT))

      if (normalized.nonEmpty && normalized.forall(ZReferenceValidator.isValid)) {
        cleanup(normalized.distinct)
      } else {
        Future.successful(BadRequest)
      }
  }

  private def cleanupResult(description: String)(deletions: Future[Long]): Future[Result] =
    deletions
      .map { deletedCount =>
        logger.info(s"[TestOnlyMonthlyReturnController] Deleted [$deletedCount] records: $description")
        NoContent
      }
      .recover { case NonFatal(exception) =>
        logger.error(s"[TestOnlyMonthlyReturnController] Failed to delete $description", exception)
        ServiceUnavailable
      }
}
