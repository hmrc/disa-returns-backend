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

import play.api.http.HeaderNames.LOCATION
import play.api.Logging
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}
import uk.gov.hmrc.disareturnsbackend.models.{CreateFileUploadRequest, CreateMonthlyReturnRequest, UpdateNilReturnRequest}
import uk.gov.hmrc.disareturnsbackend.services.{CreateFileUploadResult, CreateMonthlyReturnResult, DeclareMonthlyReturnResult, MonthlyReturnService}
import uk.gov.hmrc.disareturnsbackend.validators.ValidationHelper
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.play.bootstrap.controller.WithJsonBody

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class MonthlyReturnController @Inject() (
  cc: ControllerComponents,
  monthlyReturnService: MonthlyReturnService
)(implicit ec: ExecutionContext)
    extends BackendController(cc)
    with WithJsonBody
    with Logging {

  def getMonthlyReturn(zReference: String, taxYear: String, month: String): Action[AnyContent] =
    Action.async {
      logger.info(
        s"[MonthlyReturnController][getMonthlyReturn] Get monthly return request for zReference [$zReference], taxYear [$taxYear], month [$month]"
      )

      withValidMonthlyReturnParams(zReference, taxYear, month) { (validZReference, validTaxYear, validMonth) =>
        monthlyReturnService
          .get(validZReference, validTaxYear, validMonth)
          .map {
            case Some(monthlyReturn) => Ok(Json.toJson(monthlyReturn))
            case None                => NotFound
          }
          .recover { case NonFatal(_) => ServiceUnavailable }
      }
    }

  def createMonthlyReturn(zReference: String, taxYear: String, month: String): Action[JsValue] =
    Action.async(parse.json) { implicit request =>
      withValidMonthlyReturnParams(zReference, taxYear, month) { (validZReference, validTaxYear, validMonth) =>
        withJsonBody[CreateMonthlyReturnRequest] { createRequest =>
          logger.info(
            s"[MonthlyReturnController][createMonthlyReturn] Create monthly return request for zReference [$validZReference], taxYear [$validTaxYear], month [$validMonth], nilReturn [${createRequest.nilReturn}]"
          )

          monthlyReturnService
            .create(validZReference, validTaxYear, validMonth, createRequest.nilReturn)
            .map {
              case CreateMonthlyReturnResult.Created       => Created.withHeaders(LOCATION -> request.path)
              case CreateMonthlyReturnResult.AlreadyExists => Conflict
            }
            .recover { case NonFatal(_) => ServiceUnavailable }
        }
      }
    }

  def updateNilReturn(zReference: String, taxYear: String, month: String): Action[JsValue] =
    Action.async(parse.json) { implicit request =>
      withValidMonthlyReturnParams(zReference, taxYear, month) { (validZReference, validTaxYear, validMonth) =>
        withJsonBody[UpdateNilReturnRequest] { updateRequest =>
          logger.info(
            s"[MonthlyReturnController][updateNilReturn] Update nilReturn request for zReference [$validZReference], taxYear [$validTaxYear], month [$validMonth], value [${updateRequest.value}]"
          )

          monthlyReturnService
            .updateNilReturn(validZReference, validTaxYear, validMonth, updateRequest.value)
            .map {
              case Some(monthlyReturn) => Ok(Json.toJson(monthlyReturn))
              case None                => NotFound
            }
            .recover { case NonFatal(_) => ServiceUnavailable }
        }
      }
    }

  def declareMonthlyReturn(zReference: String, taxYear: String, month: String): Action[AnyContent] =
    Action.async {
      logger.info(
        s"[MonthlyReturnController][declareMonthlyReturn] Declare monthly return request for zReference [$zReference], taxYear [$taxYear], month [$month]"
      )

      withValidMonthlyReturnParams(zReference, taxYear, month) { (validZReference, validTaxYear, validMonth) =>
        monthlyReturnService
          .declare(validZReference, validTaxYear, validMonth)
          .map {
            case DeclareMonthlyReturnResult.Declared(monthlyReturn)  => Ok(Json.toJson(monthlyReturn))
            case DeclareMonthlyReturnResult.AlreadyDeclared          => Conflict
            case DeclareMonthlyReturnResult.MonthlyReturnNotFound    => NotFound
            case DeclareMonthlyReturnResult.OutsideDeclarationPeriod => UnprocessableEntity
          }
          .recover { case NonFatal(_) => ServiceUnavailable }
      }
    }

  def createFileUpload(zReference: String, taxYear: String, month: String): Action[JsValue] =
    Action.async(parse.json) { implicit request =>
      withValidMonthlyReturnParams(zReference, taxYear, month) { (validZReference, validTaxYear, validMonth) =>
        withJsonBody[CreateFileUploadRequest] { createRequest =>
          logger.info(
            s"[MonthlyReturnController][createFileUpload] Create file upload request for zReference [$validZReference], taxYear [$validTaxYear], month [$validMonth], upload reference [${createRequest.reference}]"
          )

          monthlyReturnService
            .createFileUpload(validZReference, validTaxYear, validMonth, createRequest.reference)
            .map {
              case CreateFileUploadResult.FileUploadCreated(_)         =>
                Created.withHeaders(LOCATION -> s"${request.path}/${createRequest.reference}")
              case CreateFileUploadResult.FileUploadAlreadyExists      => Conflict
              case CreateFileUploadResult.MonthlyReturnAlreadyDeclared => UnprocessableEntity
              case CreateFileUploadResult.MonthlyReturnNotFound        => NotFound
            }
            .recover { case NonFatal(_) => ServiceUnavailable }
        }
      }
    }

  def getFileUpload(zReference: String, taxYear: String, month: String, reference: String): Action[AnyContent] =
    Action.async {
      logger.info(
        s"[MonthlyReturnController][getFileUpload] Get file upload request for zReference [$zReference], taxYear [$taxYear], month [$month], upload reference [$reference]"
      )

      withValidMonthlyReturnParams(zReference, taxYear, month) { (validZReference, validTaxYear, validMonth) =>
        monthlyReturnService
          .getFileUpload(validZReference, validTaxYear, validMonth, reference)
          .map {
            case Some(fileUpload) => Ok(Json.toJson(fileUpload))
            case None             => NotFound
          }
          .recover { case NonFatal(_) => ServiceUnavailable }
      }
    }

  def deleteFileUpload(zReference: String, taxYear: String, month: String, reference: String): Action[AnyContent] =
    Action.async {
      logger.info(
        s"[MonthlyReturnController][deleteFileUpload] Delete file upload request for zReference [$zReference], taxYear [$taxYear], month [$month], upload reference [$reference]"
      )

      withValidMonthlyReturnParams(zReference, taxYear, month) { (validZReference, validTaxYear, validMonth) =>
        monthlyReturnService
          .deleteFileUpload(validZReference, validTaxYear, validMonth, reference)
          .map {
            case true  => NoContent
            case false => NotFound
          }
          .recover { case NonFatal(_) => ServiceUnavailable }
      }
    }

  private def withValidMonthlyReturnParams(
    zReference: String,
    taxYear: String,
    month: String
  )(block: (String, String, Int) => Future[Result]): Future[Result] =
    ValidationHelper.validateParams(zReference, taxYear, month) match {
      case Right((validZReference, validTaxYear, validMonth)) =>
        block(validZReference, validTaxYear, validMonth)

      case Left(errorMessage) =>
        logger.warn(
          s"[MonthlyReturnController][withValidMonthlyReturnParams] Invalid monthly return request parameters for zReference [$zReference], taxYear [$taxYear], month [$month]: [$errorMessage]"
        )
        Future.successful(BadRequest(Json.obj("message" -> errorMessage)))
    }
}
